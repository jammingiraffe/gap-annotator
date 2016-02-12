package gapping;

import edu.stanford.nlp.ling.CoreAnnotation;

public class GappingAnnotation implements CoreAnnotation<String> {

	public GappingAnnotation() {}

	@Override
	public Class<String> getType() {
		return String.class;
	}

}
