package gapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;


public class GappingAnnotator implements Annotator {

	private final String name;
	private final Properties props;
	private final Set<Requirement> GAPPING_ELLIPSIS = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, LEMMA_REQUIREMENT, DEPENDENCY_REQUIREMENT));
	private final Set<Requirement> GAPPING_SATISFIES = Collections.unmodifiableSet(new ArraySet<>(DEPENDENCY_REQUIREMENT));
	
	
	public GappingAnnotator(String n, Properties p) {
		this.name = n;
		this.props = p;
	}

	@Override
	public void annotate(Annotation document) {
		// Iterate over all of the sentences found
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
	    
        for(CoreMap sentence: sentences) {
	 
        	// gather all dependencies for this sentence
        	List<TypedDependency> alldeps = (List<TypedDependency>) sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class).typedDependencies();

        	// sort the dependencies by the dependent token's index
        	Comparator<TypedDependency> comparator = new Comparator<TypedDependency>() {
        	    public int compare(TypedDependency td1, TypedDependency td2) {
        	        return td1.dep().index() - td2.dep().index();
        	    }
        	};     	
        	Collections.sort(alldeps, comparator);
	    	
	    	// find root
	    	IndexedWord root = null;
	    	for (TypedDependency td : alldeps) {
	    		if (td.reln().toString().equals("root")) {
	    			root = td.dep();
	    			break;
	    		}
	    	}
	    	
	    	if (!root.equals(null)) {
	    		
	    		// initially assume root is verb (usually true)
	    		String verbstr = root.lemma();
	    		
	    		// check for open clausal complements (xcomp) connected to root
	    		IndexedWord xcomp = root;
	    		boolean searching = true;
	    		while (searching) {
	    			searching = false;
	    			for (TypedDependency td : alldeps) {
	    				if (td.gov().equals(xcomp) && td.reln().toString().equals("xcomp")) {
	    					searching = true;
	    					xcomp = td.dep();
	    					verbstr += " to " + xcomp.word();
	    					break;
	    				}
	    			}
	    		}
	    		
	    		// initialize ints to keep track of gapping index and pattern type
	    		int gapind = 0;
	    		int type = 0;
	    		
	    		// to store relevant dependencies
	    		ArrayList<TypedDependency> items = new ArrayList<TypedDependency>();
	    		
	    		// if it's a gapped sentence, adds direct objects, gapping comma, and period to items (TYPE1)
	    		// OR direct object, conjunction between verb and 2nd subject, and period (TYPE2)
	    		// OR direct object, complements, gapping comma, and period (TYPE5)
	    		// OR complements, gapping comma, and period (TYPE3)
	    		// OR 1st complement, conjunction between verb and 2nd subject, and period (TYPE4)
	    		// OR direct object, 1st complement, conjunction between verb and 2nd subject, and period (TYPE6 and TYPE8)
	    		// OR direct objects, 1st complement, gapping comma, and period (TYPE7)
	    		// OR root/verb dependency, conjunction between root (pred1) and pred2, and period (TYPE9)
	    		// OR root/verb dependency, conjunction between root (pred1) and subj2, and period (TYPE10)
	    		for (TypedDependency td : alldeps) {
	    			if ((td.gov().equals(root) || td.gov().equals(xcomp)) &&
	    					(td.reln().toString().equals("dobj") || (td.reln().toString().equals("punct") && !td.dep().word().equals(".")) ||
	    					td.reln().toString().equals("conj") || td.reln().toString().contains("nmod") ||
	    					td.reln().toString().equals("advcl") || td.reln().toString().equals("advmod") ||
	    					td.reln().toString().equals("cop"))) {
	    				items.add(td);
	    			}
	    		}
	    		// TYPE1 or TYPE3 or TYPE6
	    		if (items.size() == 3) {
	    			
	    			// TYPE1: items should contain dobj, punct, dobj||nmod
	    			if (items.get(0).reln().toString().equals("dobj") && items.get(1).reln().toString().equals("punct") &&
	    					(items.get(2).reln().toString().equals("dobj") || items.get(2).reln().toString().contains("nmod"))) {
	    				type = 1;
	    				
	    				// get index of comma for later gap filling
	    				gapind = items.get(1).dep().index();
	    			}
	    			
	    			// TYPE3: items should contain nmod||advcl||advmod||nmod:tmod, punct, nmod||advcl||advmod||nmod:tmod
	    			else if ((items.get(0).reln().toString().contains("nmod") || items.get(0).reln().toString().equals("advcl") ||
	    						items.get(0).reln().toString().equals("advmod")) && items.get(1).reln().toString().equals("punct")
	    					&& (items.get(2).reln().toString().contains("nmod") || items.get(2).reln().toString().equals("advcl") ||
	    							items.get(2).reln().toString().equals("advmod"))) {
	    				type = 3;
	    				
	    				// get index of comma for later gap filling
	    				gapind = items.get(1).dep().index();
	    			}
	    			
	    			// TYPE6 or TYPE8: items should contain dobj, advmod, conj
	    			else if (items.get(0).reln().toString().equals("dobj") && items.get(1).reln().toString().equals("advmod") &&
	    					items.get(2).reln().toString().equals("conj")) {
	    				
	    				IndexedWord subj2 = items.get(2).dep();
	    				
	    				for (TypedDependency td : alldeps) {
	    	    			if (td.gov().equals(subj2) && (td.reln().toString().equals("punct") 
	    	    					|| td.reln().toString().equals("advmod") || td.reln().toString().equals("dep")
	    	    					|| td.reln().toString().equals("appos"))) {
	    	    				items.add(td);
	    	    			}
	    	    		}
	    				
	    				// TYPE6 or TYPE8: items should now contain punct, (advmod || dep || appos) at end
	    				if (items.size() == 5) {
	    					
	    					if (items.get(3).reln().toString().equals("punct") && (items.get(4).reln().toString().equals("advmod") 
	    							|| items.get(4).reln().toString().equals("dep") || items.get(4).reln().toString().equals("appos"))) {
	    						
	    						IndexedWord obj2 = items.get(4).dep();
	    						
	    						// TYPE8: search for 2nd complement
	    						for (TypedDependency td : alldeps) {
	    	    	    			if (td.gov().equals(obj2) && td.reln().toString().equals("advmod")) {
	    	    	    				items.add(td);
	    	    	    			}
	    	    	    		}
	    						
	    						// TYPE8: items should now contain advmod at end
	    						if (items.size() == 6) {
		    	    				type = 8;
	    						}
	    						
	    						// else TYPE6
	    						else {
		    	    				type = 6;
	    						}
	    	    				
	    	    				// get index of comma for later gap filling (same for TYPE6 and TYPE8)
	    	    				gapind = items.get(3).dep().index();
	    					}
	    				}
	    			}
	    		}
	    		
	    		// TYPE2 or TYPE4 or TYPE9 or TYPE10
	    		else if (items.size() == 2) {
	    			
	    			// TYPE2: items should contain dobj, conj
	    			// TYPE4: items should contain advmod, conj
	    			if ((items.get(0).reln().toString().equals("dobj") || items.get(0).reln().toString().equals("advmod")) 
	    					&& items.get(1).reln().toString().equals("conj")) {
	    				
	    				IndexedWord subj2 = items.get(1).dep();
	    				
	    				for (TypedDependency td : alldeps) {
	    	    			if (td.gov().equals(subj2) && (td.reln().toString().equals("punct") || td.reln().toString().equals("appos") ||
	    	    					td.reln().toString().equals("advmod") || td.reln().toString().equals("dep"))) {
	    	    				items.add(td);
	    	    			}
	    	    		}
	    				
	    				if (items.size() == 4) {
	    					
	    					// TYPE2: items should now contain punct, appos at end
	    					if (items.get(2).reln().toString().equals("punct") && items.get(3).reln().toString().equals("appos")) {
	    	    				type = 2;
	    	    				
	    	    				// get index of comma for later gap filling
	    	    				gapind = items.get(2).dep().index();
	    					}
	    					
	    					// TYPE4: items should now contain punct, (advmod || dep) at end
	    					else if (items.get(2).reln().toString().equals("punct") && (items.get(3).reln().toString().equals("advmod") 
	    							|| items.get(3).reln().toString().equals("dep"))) {
	    	    				type = 4;
	    	    				
	    	    				// get index of comma for later gap filling
	    	    				gapind = items.get(2).dep().index();
	    					}
	    				}
	    			}
	    			
	    			// TYPE9 or TYPE10: items should contain cop, conj
	    			else if (items.get(0).reln().toString().equals("cop") && items.get(1).reln().toString().equals("conj")) {
	    				
	    				// don't initially know if it's subj2 or pred2
	    				IndexedWord dep = items.get(1).dep();
	    				
	    				for (TypedDependency td : alldeps) {
	    	    			if (td.gov().equals(dep) && (td.reln().toString().equals("nsubj") || td.reln().toString().equals("punct") || 
	    	    					td.reln().toString().equals("appos") || td.reln().toString().equals("advmod") || td.reln().toString().equals("amod"))) {
	    	    				items.add(td);
	    	    			}
	    	    		}
	    				
	    				// TYPE9: items should now contain nsubj at end
	    				if (items.size() == 3) {
	    					// dependent of nsubj relation (pred2 is governor)
	    					IndexedWord subj2 = items.get(2).dep();
	    					
	    					for (TypedDependency td : alldeps) {
		    	    			if (td.gov().equals(subj2) && td.reln().toString().equals("punct")) {
		    	    				items.add(td);
		    	    			}
		    	    		}
	    					
	    					// TYPE9: items should now contain punct at end
	    					if (items.size() == 4) {
	    	    				type = 9;
	    	    				
	    	    				// get index of comma for later gap filling
	    	    				gapind = items.get(3).dep().index();
	    	    				
	    	    				// change verbstr to not root
	    	    				verbstr = items.get(0).dep().lemma();
	    					}
	    				}
	    				
	    				// TYPE10: items should now contain punct, appos||advmod||amod at end
	    				else if (items.size() == 4) {
	    					if (items.get(2).reln().toString().equals("punct") && (items.get(3).reln().toString().equals("appos")
	    							|| items.get(3).reln().toString().equals("advmod") || items.get(3).reln().toString().equals("amod"))) {
	    	    				type = 10;
	    	    				
	    	    				// get index of comma for later gap filling
	    	    				gapind = items.get(2).dep().index();
	    	    				
	    	    				// change verbstr to not root
	    	    				verbstr = items.get(0).dep().lemma();
	    					}
	    				}
	    			}
	    		}
	    		
	    		// TYPE5 or TYPE7
	    		else if (items.size() == 4) {
	    			
	    			// TYPE5: items should contain dobj, nmod||advcl||advmod||nmod:tmod, punct, nmod||advcl||advmod||nmod:tmod
	    			if (items.get(0).reln().toString().equals("dobj")
	    					&& (items.get(1).reln().toString().contains("nmod") || items.get(1).reln().toString().equals("advcl") ||
	    						items.get(1).reln().toString().equals("advmod")) && items.get(2).reln().toString().equals("punct")) {
	    				
	    				if (items.get(3).reln().toString().contains("nmod") || items.get(3).reln().toString().equals("advcl") ||
	    							items.get(3).reln().toString().equals("advmod")) {
	    					type = 5;
	    				
	    					// get index of comma for later gap filling
	    					gapind = items.get(2).dep().index(); 
	    				}
	    				
	    				// TYPE7: elements 0,1,2 same as TYPE5; element 3 should be dobj
	    				else if (items.get(3).reln().toString().equals("dobj")) {
	    					IndexedWord obj2 = items.get(3).dep();
		    				
		    				for (TypedDependency td : alldeps) {
		    	    			if (td.gov().equals(obj2) && ((td.reln().toString().contains("nmod") && !td.reln().toString().contains("poss")) || 
		    	    					td.reln().toString().equals("advcl") || td.reln().toString().equals("advmod"))) {
		    	    				items.add(td);
		    	    			}
		    	    		}
		    				
		    				// TYPE7 should now contain dependency for 2nd complement at end
		    				if (items.size() == 5) {
		    					type = 7;
		    				
		    					// get index of comma for later gap filling
		    					gapind = items.get(2).dep().index(); 
		    				}
	    				}
	    			}
	    			
	    			
	    		}
	    		
	    		// make string for gapped direct object if applicable, otherwise string remains empty
	    		String gappedObj = "";
	    		if (type==5 || type==6) {
	    			IndexedWord obj = items.get(0).dep();
	    			ArrayList<Integer> objDeps = new ArrayList<Integer>();
	    			objDeps.add(obj.index());
	    			
	    			// get all relations with d.o. as governor, add the index of the dependent word to objDeps
	    			for (TypedDependency td : alldeps) {
    	    			if (td.gov().equals(obj)) {
    	    				objDeps.add(td.dep().index());
    	    			}
    	    		}
	    			
	    			// sort lowest to highest index
	    			Collections.sort(objDeps);
	    			
	    			// direct object string should span from lowest index to highest index of words dependent on direct object
	    			// (index -1 to account for ROOT index in dependency tree)
	    			for (int i=objDeps.get(0); i<=objDeps.get(objDeps.size()-1); i++) {
	    				gappedObj += " " + sentence.get(TokensAnnotation.class).get(i-1).word();
	    			}
	    		}

	    		// fill gap if a gap was found (index -1 to account for ROOT index)
	    		if (gapind>0) {
	    			sentence.get(TokensAnnotation.class).get(gapind-1).set(GappingAnnotation.class, verbstr+gappedObj);
	    		}
	    	}
	    	
	    	else {
	    		//System.err.println("Root not found.");
	    	}
        }

	}

	@Override
	public Set<Requirement> requirementsSatisfied() {
		return GAPPING_SATISFIES;
	}

	@Override
	public Set<Requirement> requires() {
		return GAPPING_ELLIPSIS;
	}
	
	// no properties are currently implemented in this annotator
	public static String signature(String annotatorName, Properties props) {
		return annotatorName;
	}

}
