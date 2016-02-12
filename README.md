# gap-annotator
## An extension for Stanford CoreNLP that handles gapping ellipsis


Note: The quality of the annotations produced by this extension is dependent on the quality of the output from the Stanford CoreNLP parser. The Stanford parser's treatment of sentences that contain gapping is highly inconsistent, so this extension only handles the most frequently occurring patterns.


### GappingAnnotator:
This class is an extension of the Stanford CoreNLP library's Annotator class. Its annotate method checks each sentence of a document to see if it contains gapping ellipsis. If gapping is found, it compiles the gapped information and adds it to the location of the gap as an annotation.


### GappingAnnotation:
This class is an extension of the Stanford CoreNLP library's CoreAnnotation class. Its purpose is to hold information generated and added by the GappingAnnotator class.


### GappingAnnotatorDemo:
This is a simple example of how to use the GappingAnnotator and GappingAnnotation classes. It asks the user to input text, then runs an annotation pipeline on the text. Included in the annotation pipeline is the GappingAnnotator. Lastly, it prints the user's sentences with their gapping annotations.
