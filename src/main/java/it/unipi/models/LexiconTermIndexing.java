package it.unipi.models;

import java.util.ArrayList;

public class LexiconTermIndexing extends LexiconTerm {

    // posting list of the term used during Indexing
    protected ArrayList<Integer> postingListDocIds = new ArrayList<>();
    protected ArrayList<Integer> postingListFrequencies = new ArrayList<>();
    private int lastDocIdInserted;


    public ArrayList<Integer> getPostingListDocIds() {
        return postingListDocIds;
    }

    public ArrayList<Integer> getPostingListFrequencies() {
        return postingListFrequencies;
    }

    public void setPostingListDocIds(ArrayList<Integer> postingListDocIds) {
        this.postingListDocIds = postingListDocIds;
    }

    public LexiconTermIndexing() {
        super();
        lastDocIdInserted = -1;
    }

    public LexiconTermIndexing(String term) {
        super(term);
        lastDocIdInserted = -1;
    }

    // used for the initial creation of the partial posting lists
    public void addToPostingList(int docID) {
        if(lastDocIdInserted != docID){
            // new document, thus new posting
            lastDocIdInserted = docID;
            postingListDocIds.add(docID);
            postingListFrequencies.add(1);
            documentFrequency++;
        }
        else{
            // additional occurrence for the previous document
            Integer frequency = postingListFrequencies.get(postingListFrequencies.size() - 1);
            postingListFrequencies.set(postingListFrequencies.size() - 1, frequency + 1);
        }
    }

}
