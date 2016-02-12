package gapping;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Properties;
import java.util.Scanner;


public class GappingAnnotatorDemo {
	public static void main(String[] args) {
		
	    // initialize pipeline
	    Properties props = new Properties();
	    props.put("customAnnotatorClass.gapellipsis", "gapping.GappingAnnotator");
        props.put("annotators", "tokenize, ssplit, pos, lemma, depparse, gapellipsis");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	    
        // take user input for text to be analyzed
	    Scanner in = new Scanner(System.in);
	    String text;
	    System.out.println("Enter the sentence(s) you would like to annotate:");
	    text = in.nextLine();
	    in.close();
	    
	    // create an empty Annotation with the given text
        Annotation document = new Annotation(text);

        // run all Annotators on this text
        pipeline.annotate(document);

        // print results of gapping ellipsis annotation
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);  
        for(CoreMap sentence: sentences) {
        	System.out.println("Original Sentence: " + sentence);
        	String newsent = sentence.get(TokensAnnotation.class).get(0).word();
    		for (int i=1; i<sentence.get(TokensAnnotation.class).size(); i++) {
    			newsent += " ";
    			CoreLabel token = sentence.get(TokensAnnotation.class).get(i);
    			if (token.containsKey(GappingAnnotation.class)) {
    					newsent += "[" + token.get(GappingAnnotation.class) + "]";
    			}
    			else {
    				newsent += token.word();
    				}    				
    		}   		
    		System.out.println("New Sentence: " + newsent + "\n");
        }    
	}
}
