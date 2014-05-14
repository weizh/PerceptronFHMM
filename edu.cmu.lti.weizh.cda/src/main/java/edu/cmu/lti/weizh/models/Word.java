package edu.cmu.lti.weizh.models;


public class Word {

  String word;

  String pos;
  
  public Word(String string) {
    this.word = string;
  }

  public Word(String string, String pos) {
    this.word = string;
    this.pos= pos;
  }

  public String getLemma() {
    return word.toLowerCase();
  }
  
  public String getPos() {
	return pos;
}

public void setPos(String pos) {
	this.pos = pos;
}

public String toString(){
	return word;
	  
  }
}
