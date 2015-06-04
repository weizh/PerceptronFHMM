package edu.cmu.lti.weizh.train.conll2k;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.lti.weizh.data.CONLLFormatDataSet;
import edu.cmu.lti.weizh.data.DataFactory;
import edu.cmu.lti.weizh.docmodel.Sentence;
import edu.cmu.lti.weizh.docmodel.Word;
import edu.cmu.lti.weizh.feature.FCONST;
import edu.cmu.lti.weizh.feature.Feature;
import edu.cmu.lti.weizh.feature.Theta;
import edu.cmu.lti.weizh.mlmodel.FDMM;
import edu.cmu.lti.weizh.train.AbstractFDMMTrainer;

@Deprecated
public class C2K_TrigramFDMM_Train extends AbstractFDMMTrainer<String, C2K_TrigramFDMM_Train, CONLLFormatDataSet> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	C2K_TrigramFDMM_Train(String[] thetaHeaders, String thetaHeaderDelimiter, String thetaValueDelimiter, String[] featureHeaders,
			String featureHeaderDelimiter, String featureValueDelimiter) {
		super(thetaHeaders, thetaHeaderDelimiter, thetaValueDelimiter, featureHeaders, featureHeaderDelimiter,
				featureValueDelimiter);
	}

	public C2K_TrigramFDMM_Train() {
		super();
	}

	@Override
	public void train(CONLLFormatDataSet d) throws Exception {
		for (Sentence s : d.getDocuments().get(0).getParagraphs().get(0).getSentences()) {
			List<Word> words = s.getWords();
			fdmm.add("<s>", FCONST.SENTSTART, new Feature<String>(FCONST.n(FCONST.F_CHUNK, 1), s, -1));

			for (int i = 0; i < words.size(); i++) {

				Word w = words.get(i);

				Theta<String> theta = new Theta<String>(w);

				List<Feature<String>> features = new ArrayList<Feature<String>>(featureHeaders.length);
				for (String fheader : featureHeaders) {
					Feature<String> f = new Feature<String>(fheader, s, i);
					features.add(f);
				}

				fdmm.add(theta, w.getChunkType(), features);

				fdmm.add(theta, w.getChunkType(), new Feature<String>(FCONST.n(FCONST.F_CHUNK, 1), s, i));

				if (i == words.size() - 1) {
					fdmm.add(theta, w.getChunkType(), new Feature<String>(FCONST.n(FCONST.F_CHUNK, 1), s, s.getWords().size()));
				}
			}

		}
	}

	public FDMM getFDMMModel() {
		return this.fdmm;
	}


	public static void main(String argb[]) {
		String thd = "-thd-";
		String tvd = "-tvd-";
		String[] thetaHeaders = new String[] { FCONST.T_WORD, FCONST.T_LEMMA + thd + FCONST.T_SUFFIX, FCONST.T_LEMMA,
				FCONST.T_WORDFORM + thd + FCONST.T_SUFFIX, FCONST.T_POS + thd + FCONST.T_SUFFIX, FCONST.T_POS, FCONST.DUMMY };

		String[] featureHeaders = new String[] { FCONST.F_WORD, FCONST.F_LEMMA, FCONST.F_WORDFORM, FCONST.F_POS, FCONST.F_SUFFIX,

		FCONST.p(FCONST.F_POS, 1), FCONST.n(FCONST.F_POS, 1),

		FCONST.p(FCONST.F_LEMMA, 1), FCONST.p(FCONST.F_LEMMA, 2), FCONST.n(FCONST.F_LEMMA, 1), FCONST.n(FCONST.F_LEMMA, 2),

		FCONST.p(FCONST.F_SUFFIX, 1), FCONST.n(FCONST.F_SUFFIX, 1),

		FCONST.p(FCONST.F_PREFIX, 1), FCONST.p(FCONST.F_PREFIX, 2),

		};
		String fhd = "-fhd-";
		String fvd = "-fvd-";

		C2K_TrigramFDMM_Train trainer = new C2K_TrigramFDMM_Train(thetaHeaders, thd, tvd, featureHeaders, fhd, fvd);

		CONLLFormatDataSet train2kData = DataFactory.getCONLL2kTrain();

		try {
			trainer.train(train2kData);
			trainer.store("CONLL2kFDMMTrainer");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	protected C2K_TrigramFDMM_Train self() {
		// TODO Auto-generated method stub
		return this;
	}
}
