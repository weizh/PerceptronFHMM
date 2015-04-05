package edu.cmu.lti.weizh.train.fdmm.online;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.cmu.lti.weizh.docmodel.Document;
import edu.cmu.lti.weizh.docmodel.NamedEntity;
import edu.cmu.lti.weizh.docmodel.Paragraph;
import edu.cmu.lti.weizh.docmodel.Sentence;
import edu.cmu.lti.weizh.docmodel.Word;
import edu.cmu.lti.weizh.eval.Prediction;
import edu.cmu.lti.weizh.eval.Result;
import edu.cmu.lti.weizh.mlmodel.FDA;
import edu.cmu.lti.weizh.mlmodel.FDMM;
import edu.cmu.lti.weizh.nlp.EuroLangTwokenizer;
import edu.cmu.lti.weizh.train.FeatureConstants;
import edu.cmu.lti.weizh.train.fdmm.cli.FDA_POS_Tagger;
import edu.cmu.lti.weizh.utils.Stemmer;

public class TweetOnlineLearner {

	static final String path = "/home/wei/Documents/activeLearning/readable";

	static FDMM posTagger;
	static {
		try {
			posTagger = FDMM.load("POS-rich-randomSent-fold10.0-NOV08.en.FDA_MLModel");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	static Random r = new Random();

	public static void main(String argv[]) throws Exception {

		/*
		 * To find those files, first go to src/main/resources, uncompress the train_test_data.tar.gz
		 * in the folder.
		 * 
		 */
		
		
		String startFileName = "resources/seed.txt";
		String testFile = "resources/test.txt";
		String activeTrain = "resources/AL.txt";

		System.out.println("loading seed data");
		Document startData = readLabeledData(startFileName);
		System.out.println("loading test data");
		Document testData = readLabeledData(testFile);

		boolean loadPrevious = false;
		Document activeLearnData = null;
		ArrayList<Result> results;
		FDA activeModel;
		// load them next time when you want to continue the previous
		// experiments or get back to an original point.
		if (loadPrevious) {
			activeLearnData = (Document) loadData("al/1.activeLearnData");
			results = (ArrayList<Result>) loadData("al/1.results");
			activeModel = FDA.load("al/1.model");

		} else {
			System.out.println("loading active train data");
			activeLearnData = readUnlabeledData(activeTrain);
			results = new ArrayList<Result>();
			activeModel = new FDA();
			System.out.println("training the seed data");
			trainDocument(activeModel, startData);
		}

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = null;
		while (true) {
			System.out.println("Input any command to start. Type quit to quit.");
			line = br.readLine();
			if (line.equalsIgnoreCase("quit"))
				break;

			System.out.println("The following sentence is chosen:");
			// label the sentence in the activeLearnData as learned, so that
			// we don't choose it next time.
			// return the reference of the sentence, not to detach from the
			// activeLearn data.
			Sentence sent = chooseNextSentence(activeLearnData, startData, activeModel);
			System.out.println(sent.getPlainSentence());

			if (sent.getNamedEntities().size() == 0) {
				System.out.println("But no named entitties. continue;");
				continue;
			}

			for (NamedEntity ne : sent.getNamedEntities()) {
				System.out.println("Do you think the word [" + ne.getLemma() + "] is what type?");
				System.out.println("1 for time, 2 for location, 3 for none.");
				line = br.readLine();
				while (!(line.trim().equals("1") || line.trim().equals("2") || line.trim().equals("3"))) {
					System.out.println("Retype the type:");
					line = br.readLine();
				}
				ne.setEntityType(line);
			}

			System.out.println("Model learn this sentence:");
			trainSentence(activeModel, sent);

			System.out.println("Evaluate the new model:");
			Result result = testDataSet(activeModel, startData, activeLearnData, testData);

			// calculateGradient(results, result);
			results.add(result);
			printResults(results);

		}

		int time = 1;
		System.out.println("file name for the model to store:");
		activeModel.store("al/" + time + ".model");
		System.out.println("Input the file name for active learning data:");
		storeData(activeLearnData, "al/" + time + ".activeLearnData");
		System.out.println("Input the file name for result:");
		storeData(results, "al/" + time + ".results");
		System.out.println("Done.");
	}

	private static void printResults(ArrayList<Result> results) {

		System.out.println("Results:");
		for (Result r : results) {
			System.out.println(r.totalF1 + "\t");
		}
		System.out.println("Total added data is:");
		System.out.println(results.get(results.size() - 1).addedTrainCount);
	}

	private static Result testDataSet(FDA activeModel, Document startData, Document activeLearnData,
			Document testData) throws Exception {

		Result res = new Result();

		double time = 0, location = 0, others = 0;
		double totalPredTime = 0, totalPredLocation = 0, totalPredOthers = 0;
		double totalTime = 0, totalLocation = 0, totalOthers = 0;

		for (Sentence sent : testData.getParagraphs().get(0).getSentences()) {
			Prediction p = testSentence(activeModel, sent);
			if (p == null) {
				System.out.println("Sentence does not have prediction because no entity.");
				continue;
			}
			String goldStandard = sent.getNamedEntities().get(0).getEntityType();

			if (goldStandard.equals("1"))
				totalTime++;
			else if (goldStandard.equals("2"))
				totalLocation++;
			else
				totalOthers++;

			if (p.getBestCandidateName().equals("1"))
				totalPredTime++;
			else if (p.getBestCandidateName().equals("2"))
				totalPredLocation++;
			else
				totalPredOthers++;

			if (goldStandard.equalsIgnoreCase(p.getBestCandidateName())) {
				if (goldStandard.equals("1"))
					time++;
				else if (goldStandard.equals("2"))
					location++;
				else
					others++;
			}
		}
		res.lprecision = location / totalPredLocation;
		res.tprecision = time / totalPredTime;
		res.oprecision = others / totalPredOthers;

		res.lrecall = location / totalLocation;
		res.trecall = time / totalTime;
		res.orecall = others / totalOthers;

		res.lf1 = 2 * location / (totalPredLocation + totalLocation);
		res.tf1 = 2 * time / (totalPredTime + totalTime);
		res.of1 = 2 * others / (totalPredOthers + totalOthers);

		res.totalPrecision = (location + time + others) / (totalPredLocation + totalPredTime + totalPredOthers);
		res.totalRecall = (location + time + others) / (totalLocation + totalTime + totalOthers);
		res.totalF1 = 2.0 / (1.0 / res.totalPrecision + 1.0 / res.totalRecall);

		res.seedTrainCount = startData.getParagraphs().get(0).getSentences().size();
		res.testInstanceCount = testData.getParagraphs().get(0).getSentences().size();
		res.addedTrainCount = getTaggedSentences(activeLearnData);
		return res;
	}

	private static int getTaggedSentences(Document activeLearnData) throws Exception {
		int total = 0;
		for (Sentence sent : activeLearnData.getParagraphs().get(0).getSentences()) {
			if (sent.getNamedEntities().size() == 0)
				continue;
			if (sent.getNamedEntities().get(0).getEntityType() != null)
				total++;
		}
		return total;
	}

	private static Prediction testSentence(FDA fdamodel, Sentence sent) throws Exception {
		List<NamedEntity> nes = sent.getNamedEntities();
		if (nes.size() == 0) {
			return null;
		}
		NamedEntity ne = nes.get(0);

		int start = ne.getStart();
		int end = ne.getEnd();

		// entity starts the sentence
		String p1tok = "[p1tok]", p2tok = "[p2tok]", p3tok = "[p3tok]";
		String n1tok = "[n1tok]", n2tok = "[n2tok]", n3tok = "[n3tok]";

		String p1pos = "[p1pos]", p2pos = "[p2pos]", p3pos = "[p3pos]";
		String n1pos = "[n1pos]", n2pos = "[n2pos]", n3pos = "[n3pos]";

		String p1cap = "[p1cap]", p2cap = "[p2cap]", p3cap = "[p3cap]";
		String n1cap = "[n1cap]", n2cap = "[n2cap]", n3cap = "[n3cap]";

		String p1suf = "[p1suf]", p2suf = "[p2suf]", p3suf = "[p3suf]";
		String n1suf = "[n1suf]", n2suf = "[n2suf]", n3suf = "[n3suf]";

		String p1pref = "[p1pref]", p2pref = "[p2pref]", p3pref = "[p3pref]";
		String n1pref = "[n1pref]", n2pref = "[n2pref]", n3pref = "[n3pref]";

		String p1form = "[p1form]", p2form = "[p2form]", p3form = "[p3form]";
		String n1form = "[n1form]", n2form = "[n2form]", n3form = "[n3form]";

		String p1type = "[p1type]", p2type = "[p2type]", p3type = "[p3type]";
		String n1type = "[n1type]", n2type = "[n2type]", n3type = "[n3type]";

		if (start > 0) {
			p1tok = sent.wordAt(start - 1).getLemma();
			p1pos = sent.wordAt(start - 1).getPartOfSpeech();
			p1cap = sent.wordAt(start - 1).isCapitalizedFirst();
			p1suf = sent.wordAt(start - 1).getSuffix();
			p1pref = sent.wordAt(start - 1).getPreffix();
			p1form = sent.wordAt(start - 1).getWordForm();
			p1type = sent.wordAt(start - 1).getEntityType();
			if (start > 1) {
				p2tok = sent.wordAt(start - 2).getLemma();
				p2pos = sent.wordAt(start - 2).getPartOfSpeech();
				p2cap = sent.wordAt(start - 2).isCapitalizedFirst();
				p2suf = sent.wordAt(start - 2).getSuffix();
				p2pref = sent.wordAt(start - 2).getPreffix();
				p2form = sent.wordAt(start - 2).getWordForm();
				p2type = sent.wordAt(start - 2).getEntityType();
				if (start > 2) {
					p3tok = sent.wordAt(start - 3).getLemma();
					p3pos = sent.wordAt(start - 3).getPartOfSpeech();
					p3cap = sent.wordAt(start - 3).isCapitalizedFirst();
					p3suf = sent.wordAt(start - 3).getSuffix();
					p3pref = sent.wordAt(start - 3).getPreffix();
					p3form = sent.wordAt(start - 3).getWordForm();
					p3type = sent.wordAt(start - 3).getEntityType();
				}
			}
		}

		String origintok = ne.getLemma();

		if (end < sent.size() - 1) {
			n1tok = sent.wordAt(end + 1).getLemma();
			n1pos = sent.wordAt(end + 1).getPartOfSpeech();
			n1cap = sent.wordAt(end + 1).isCapitalizedFirst();
			n1suf = sent.wordAt(end + 1).getSuffix();
			n1pref = sent.wordAt(end + 1).getPreffix();
			n1form = sent.wordAt(end + 1).getWordForm();
			n1type = sent.wordAt(end + 1).getEntityType();
			if (end < sent.size() - 2) {
				n2tok = sent.wordAt(end + 2).getLemma();
				n2pos = sent.wordAt(end + 2).getPartOfSpeech();
				n2cap = sent.wordAt(end + 2).isCapitalizedFirst();
				n2suf = sent.wordAt(end + 2).getSuffix();
				n2pref = sent.wordAt(end + 2).getPreffix();
				n2form = sent.wordAt(end + 2).getWordForm();
				n2type = sent.wordAt(end + 2).getEntityType();

				if (end < sent.size() - 3) {
					n3tok = sent.wordAt(end + 3).getLemma();
					n3pos = sent.wordAt(end + 3).getPartOfSpeech();
					n3cap = sent.wordAt(end + 3).isCapitalizedFirst();
					n3suf = sent.wordAt(end + 3).getSuffix();
					n3pref = sent.wordAt(end + 3).getPreffix();
					n3form = sent.wordAt(end + 3).getWordForm();
					n3type = sent.wordAt(end + 3).getEntityType();

				}
			}
		}
		String sp1tok = Stemmer.stemTerm(p1tok);
		String sp2tok = Stemmer.stemTerm(p2tok);
		String sp3tok = Stemmer.stemTerm(p3tok);
		String sn1tok = Stemmer.stemTerm(n1tok);
		String sn2tok = Stemmer.stemTerm(n2tok);
		String sn3tok = Stemmer.stemTerm(n3tok);

		String[] thetaIndex = new String[] { origintok, "[DUMMY]" };
		String[] featurevalues = new String[] { FeatureConstants.P1TOK, sp1tok, FeatureConstants.N1TOK, sn1tok, FeatureConstants.P1POS, p1pos, FeatureConstants.N1POS,
				n1pos, FeatureConstants.P1SUF, p1suf, FeatureConstants.N1SUF, n1suf,

				// added
				FeatureConstants.P1FORM, p1form, FeatureConstants.N1FORM, n1form, FeatureConstants.P2TOK, sp2tok, FeatureConstants.N2TOK, sn2tok, FeatureConstants.P2POS, p2pos,
				FeatureConstants.N2POS, n2pos, FeatureConstants.P1FORM, p1form, FeatureConstants.N1FORM, n1form, FeatureConstants.P1CAP, p1cap, FeatureConstants.N1CAP, n1cap

		};
		Prediction p = fdamodel.predict(thetaIndex, 1E-5, 1E-5, featurevalues);
		return p;
	}

	private static void trainDocument(FDA activeModel, Document startData) throws Exception {
		if (startData.getParagraphs() == null || startData.getParagraphs().size() == 0)
			return;
		for (Sentence sent : startData.getParagraphs().get(0).getSentences()) {
			trainSentence(activeModel, sent);

		}
	}

	private static void trainSentence(FDA fdamodel, Sentence sent) {

		List<NamedEntity> nes = sent.getNamedEntities();
		if (nes.size() == 0) {
			System.out.println("Sentence does not have entity. Returned.");
			System.out.println(sent.getPlainSentence());
			return;
		}
		NamedEntity ne = nes.get(0);

		int start = ne.getStart();
		int end = ne.getEnd();

		// entity starts the sentence
		String p1tok = "[p1tok]", p2tok = "[p2tok]", p3tok = "[p3tok]";
		String n1tok = "[n1tok]", n2tok = "[n2tok]", n3tok = "[n3tok]";

		String p1pos = "[p1pos]", p2pos = "[p2pos]", p3pos = "[p3pos]";
		String n1pos = "[n1pos]", n2pos = "[n2pos]", n3pos = "[n3pos]";

		String p1cap = "[p1cap]", p2cap = "[p2cap]", p3cap = "[p3cap]";
		String n1cap = "[n1cap]", n2cap = "[n2cap]", n3cap = "[n3cap]";

		String p1suf = "[p1suf]", p2suf = "[p2suf]", p3suf = "[p3suf]";
		String n1suf = "[n1suf]", n2suf = "[n2suf]", n3suf = "[n3suf]";

		String p1pref = "[p1pref]", p2pref = "[p2pref]", p3pref = "[p3pref]";
		String n1pref = "[n1pref]", n2pref = "[n2pref]", n3pref = "[n3pref]";

		String p1form = "[p1form]", p2form = "[p2form]", p3form = "[p3form]";
		String n1form = "[n1form]", n2form = "[n2form]", n3form = "[n3form]";

		String p1type = "[p1type]", p2type = "[p2type]", p3type = "[p3type]";
		String n1type = "[n1type]", n2type = "[n2type]", n3type = "[n3type]";

		if (start > 0) {
			p1tok = sent.wordAt(start - 1).getLemma();
			p1pos = sent.wordAt(start - 1).getPartOfSpeech();
			p1cap = sent.wordAt(start - 1).isCapitalizedFirst();
			p1suf = sent.wordAt(start - 1).getSuffix();
			p1pref = sent.wordAt(start - 1).getPreffix();
			p1form = sent.wordAt(start - 1).getWordForm();
			p1type = sent.wordAt(start - 1).getEntityType();
			if (start > 1) {
				p2tok = sent.wordAt(start - 2).getLemma();
				p2pos = sent.wordAt(start - 2).getPartOfSpeech();
				p2cap = sent.wordAt(start - 2).isCapitalizedFirst();
				p2suf = sent.wordAt(start - 2).getSuffix();
				p2pref = sent.wordAt(start - 2).getPreffix();
				p2form = sent.wordAt(start - 2).getWordForm();
				p2type = sent.wordAt(start - 2).getEntityType();
				if (start > 2) {
					p3tok = sent.wordAt(start - 3).getLemma();
					p3pos = sent.wordAt(start - 3).getPartOfSpeech();
					p3cap = sent.wordAt(start - 3).isCapitalizedFirst();
					p3suf = sent.wordAt(start - 3).getSuffix();
					p3pref = sent.wordAt(start - 3).getPreffix();
					p3form = sent.wordAt(start - 3).getWordForm();
					p3type = sent.wordAt(start - 3).getEntityType();
				}
			}
		}

		String origintok = ne.getLemma();

		if (end < sent.size() - 1) {
			n1tok = sent.wordAt(end + 1).getLemma();
			n1pos = sent.wordAt(end + 1).getPartOfSpeech();
			n1cap = sent.wordAt(end + 1).isCapitalizedFirst();
			n1suf = sent.wordAt(end + 1).getSuffix();
			n1pref = sent.wordAt(end + 1).getPreffix();
			n1form = sent.wordAt(end + 1).getWordForm();
			n1type = sent.wordAt(end + 1).getEntityType();
			if (end < sent.size() - 2) {
				n2tok = sent.wordAt(end + 2).getLemma();
				n2pos = sent.wordAt(end + 2).getPartOfSpeech();
				n2cap = sent.wordAt(end + 2).isCapitalizedFirst();
				n2suf = sent.wordAt(end + 2).getSuffix();
				n2pref = sent.wordAt(end + 2).getPreffix();
				n2form = sent.wordAt(end + 2).getWordForm();
				n2type = sent.wordAt(end + 2).getEntityType();

				if (end < sent.size() - 3) {
					n3tok = sent.wordAt(end + 3).getLemma();
					n3pos = sent.wordAt(end + 3).getPartOfSpeech();
					n3cap = sent.wordAt(end + 3).isCapitalizedFirst();
					n3suf = sent.wordAt(end + 3).getSuffix();
					n3pref = sent.wordAt(end + 3).getPreffix();
					n3form = sent.wordAt(end + 3).getWordForm();
					n3type = sent.wordAt(end + 3).getEntityType();

				}
			}
		}
		String sp1tok = Stemmer.stemTerm(p1tok);
		String sp2tok = Stemmer.stemTerm(p2tok);
		String sp3tok = Stemmer.stemTerm(p3tok);
		String sn1tok = Stemmer.stemTerm(n1tok);
		String sn2tok = Stemmer.stemTerm(n2tok);
		String sn3tok = Stemmer.stemTerm(n3tok);

		for (String s : new String[] { origintok, "[DUMMY]" }) {
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1TOK, sp1tok);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2TOK, sp2tok);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3TOK, sp3tok);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1TOK, sn1tok);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2TOK, sn2tok);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3TOK, sn3tok);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1POS, p1pos);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2POS, p2pos);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3POS, p3pos);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1POS, n1pos);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2POS, n2pos);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3POS, n3pos);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1CAP, p1cap);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2CAP, p2cap);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3CAP, p3cap);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1CAP, n1cap);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2CAP, n2cap);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3CAP, n3cap);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1SUF, p1suf);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2SUF, p2suf);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3SUF, p3suf);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1SUF, n1suf);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2SUF, n2suf);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3SUF, n3suf);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1PREF, p1pref);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2PREF, p2pref);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3PREF, p3pref);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1PREF, n1pref);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2PREF, n2pref);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3PREF, n3pref);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1FORM, p1form);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2FORM, p2form);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3FORM, p3form);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1FORM, n1form);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2FORM, n2form);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3FORM, n3form);

			// add previous label feature.
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P1TYPE, p1type);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P2TYPE, p2type);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.P3TYPE, p3type);

			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N1TYPE, n1type);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N2TYPE, n2type);
			fdamodel.add(s, ne.getEntityType(), FeatureConstants.N3TYPE, n3type);
		}
	}

	private static Sentence chooseNextSentence(Document activeLearnData, Document startData, FDA activeModel)
			throws Exception {

		Sentence pickedSentence = null;
		double maxValue = Double.NEGATIVE_INFINITY;

		// pick the sentence that is disimmilar to the training data we have
		// seen, and has the smallest 1-entropy value
		for (Sentence sent : activeLearnData.getParagraphs().get(0).getSentences()) {
			List<NamedEntity> namedEntities = sent.getNamedEntities();
			if (namedEntities == null)
				continue;
			if (namedEntities.size() == 0)
				continue;
			if (namedEntities.get(0).getEntityType() != null)
				continue;
			Prediction predictions = testSentence(activeModel, sent);
			double entropy = predictions.getEntropy();
			double sentenceMaxSim = sentenceSimToTrainingData(sent, activeLearnData, startData);
			double value = 0.5 * (1 - sentenceMaxSim) + 0.5 * entropy;
//			double value = entropy;
			if (value > maxValue) {
				maxValue = value;
				pickedSentence = sent;
			}
		}
		return pickedSentence;
	}

	private static double sentenceSimToTrainingData(Sentence sent, Document activeLearnData, Document startData) throws Exception {

		// pick the sentence that has the smallest max similarity.
		// calculate the max of the similarity among the data

		double max = Double.NEGATIVE_INFINITY;
		double average = 0;
		double count=0;
		for (Sentence sent1 : activeLearnData.getParagraphs().get(0).getSentences()) {
			if (sent1.getNamedEntities() == null)
				continue;
			if (sent1.getNamedEntities().size() == 0)
				continue;
			if (sent1.getNamedEntities().get(0).getEntityType() == null)
				continue; // check training data
			if (sent1 == sent)
				continue; // if the sentence is itself, then continue;
			double sim = calculateSentenceSimilarity(sent, sent1);
			average += sim;
			count++;
			if (max < sim) {
				max = sim;
			}
		}

		for (Sentence sent1 : startData.getParagraphs().get(0).getSentences()) {
			if (sent1.getNamedEntities() == null)
				continue;
			if (sent1.getNamedEntities().size() == 0)
				continue;
			double sim = calculateSentenceSimilarity(sent, sent1);
			average += sim;
			count++;
			if (max < sim) {
				max = sim;
			}
		}

//		return max;
		return average/count;
	}

	private static double calculateSentenceSimilarity(Sentence sent, Sentence sent1) {
		if (sent.getNamedEntities().size() == 0)
			return 1;
		if (sent1.getNamedEntities().size() == 0)
			return 1;

		double num = 0;
		for (int i = 0; i < sent.getWords().size(); i++) {
			for (int j = 0; j < sent1.getWords().size(); j++) {
				if (i < sent.getNamedEntities().get(0).getStart() - 2)
					continue;
				if (i > sent.getNamedEntities().get(0).getEnd() + 2)
					continue;
				if (j < sent1.getNamedEntities().get(0).getStart() - 2)
					continue;
				if (j > sent1.getNamedEntities().get(0).getEnd() + 2)
					continue;
				if (sent.getWords().get(i).getWord().equalsIgnoreCase(sent1.getWords().get(j).getWord()))
					num++;
			}
		}
		double denom = (double) sent.getWords().size() * (double) sent1.getWords().size();
		return num / denom;
	}

	private static Object loadData(String string) throws ClassNotFoundException, IOException {
		FileInputStream fileIn = new FileInputStream(string);
		ObjectInputStream in = new ObjectInputStream(fileIn);
		Object e = in.readObject();
		in.close();
		return e;

	}

	private static void storeData(Object data, String readLine) throws IOException {
		FileOutputStream fileOut = new FileOutputStream(readLine);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(data);
	}

	private static Document readUnlabeledData(String activeTrain) throws Exception {
		Document d = new Document(activeTrain);
		Paragraph p = new Paragraph();
		d.addParagraph(p);
		BufferedReader br = Files.newBufferedReader(Paths.get(activeTrain), Charset.defaultCharset());
		String line = null;
		int i = 0;
		while ((line = br.readLine()) != null) {
//			if (r.nextDouble() < 0.7)
//				continue;
			System.out.println("Training sentence " + (i++));
			String[] toks = line.split("\t");
			String entityStr = toks[0];
			String sentStr = toks[1];
			Sentence sentence = new Sentence(sentStr);
			sentence = EuroLangTwokenizer.tokenize(sentence);
			sentence = FDA_POS_Tagger.getInstance().tag(sentence);
			fillEntity(sentence, entityStr, null);
			p.addSentence(sentence);
		}

		return d;
	}

	private static void fillEntity(Sentence sentence, String entityStr, String labelStr) {
		// System.out.println(sentence.getPlainSentence());
		// System.out.println(entityStr);
		// System.out.println(labelStr);
		// find the start and end of the named entity. Create it, and find the
		// index.
		String[] ewords = entityStr.split(" ");
		if (ewords.length == 1) {
			ArrayList<NamedEntity> nes = new ArrayList<NamedEntity>();
			for (int i = 0; i < sentence.getWords().size(); i++) {
				if (sentence.getWords().get(i).getLemma().equalsIgnoreCase(ewords[0])) {
					NamedEntity ne = new NamedEntity(labelStr, i, i, sentence);
					nes.add(ne);
				}
			}
			sentence.setNEs(nes);
		} else {
			ArrayList<NamedEntity> nes = new ArrayList<NamedEntity>();
			int eWordCount = ewords.length;
			int eWordMatched = 0;
			for (int i = 0; i < sentence.getWords().size(); i++) {
				if (sentence.getWords().get(i).getLemma().equalsIgnoreCase(ewords[eWordMatched])) {
					eWordMatched++;
					if (eWordMatched == eWordCount) {
						nes.add(new NamedEntity(labelStr, i - eWordCount + 1, i, sentence));
						break;
					}
				} else {
					eWordMatched = 0;
				}
			}
			sentence.setNEs(nes);
		}
	}

	private static Document readLabeledData(String trainFile) throws Exception {
		Document d = new Document(trainFile);
		Paragraph p = new Paragraph();
		d.addParagraph(p);
		BufferedReader br = Files.newBufferedReader(Paths.get(trainFile), Charset.defaultCharset());
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] toks = line.split("\t");
			String labelStr = toks[0];
			String entityStr = toks[1];
			String sentStr = toks[2];
			Sentence sentence = new Sentence(sentStr);
			sentence = EuroLangTwokenizer.tokenize(sentence);
			sentence = FDA_POS_Tagger.getInstance().tag(sentence);
			fillEntity(sentence, entityStr, labelStr);
			p.addSentence(sentence);
		}
		return d;
	}

	static void printFile() throws IOException {
		BufferedWriter al = Files.newBufferedWriter(Paths.get("AL.txt"), Charset.defaultCharset());
		BufferedWriter test = Files.newBufferedWriter(Paths.get("test.txt"), Charset.defaultCharset());
		BufferedWriter seed = Files.newBufferedWriter(Paths.get("seed.txt"), Charset.defaultCharset());

		Document doc = new Document("twitter-Training data");
		File f = new File(path);
		String[] subNode = f.list();
		for (String name : subNode) {
			File n = new File(f, name);
			String shortname = n.getName();
			System.out.println(shortname);
			String word = shortname.split("[\\.]")[0];
			String[] words = word.split(" ");
			BufferedReader br = Files.newBufferedReader(Paths.get(path + "/" + name), Charset.defaultCharset());
			String line = null;
			Paragraph p = new Paragraph();
			while ((line = br.readLine()) != null) {
				String wline = word + "\t" + line + "\n";
				double d = r.nextDouble();
				if (d < 0.01)
					seed.write(wline);
				else if (d < 0.05) {
					test.write(wline);
				} else {
					al.write(wline);
				}
			}
		}
		al.close();
		test.close();
		seed.close();

	}

	// document is all docs.
	// paragraph is for a word
	// sentence is a tweet for the word
	void TweetDataReader() throws IOException {

		Document d = new Document("twitter-Training data");
		File f = new File(path);
		String[] subNode = f.list();
		for (String name : subNode) {
			File n = new File(f, name);
			String shortname = n.getName();
			String word = shortname.split(".")[0];
			String[] words = word.split(" ");
			BufferedReader br = Files.newBufferedReader(Paths.get(path + name), Charset.defaultCharset());
			String line = null;
			Paragraph p = new Paragraph();
			while ((line = br.readLine()) != null) {
				Sentence sentence = EuroLangTwokenizer.tokenize(new Sentence(line));
				fillEntity(sentence, word, null);
			}
		}
	}
}