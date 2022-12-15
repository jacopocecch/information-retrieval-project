package it.unipi.models;

import it.unipi.utils.Constants;
import it.unipi.utils.Utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LexiconTerm {

    protected String term;
    //number of documents containing the term
    protected int documentFrequency;
    //number of total occurrences of the term
    protected int collectionFrequency;

    // TODO change to long
    protected long docIdsOffset;
    protected long frequenciesOffset;
    protected int docIdsSize;
    protected int frequenciesSize;

    public LexiconTerm() {
        documentFrequency = 0;
        collectionFrequency = 0;
    }

    public LexiconTerm(String term) {
        super();
        this.term = term;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public int getDocumentFrequency() {
        return documentFrequency;
    }

    public int getCollectionFrequency() {
        return collectionFrequency;
    }

    public void setDocumentFrequency(int documentFrequency) {
        this.documentFrequency = documentFrequency;
    }

    public void setCollectionFrequency(int collectionFrequency) {
        this.collectionFrequency = collectionFrequency;
    }

    public void setDocIdsOffset(long docIdsOffset) {
        this.docIdsOffset = docIdsOffset;
    }

    public long getDocIdsOffset() {
        return docIdsOffset;
    }

    public void setFrequenciesOffset(long frequenciesOffset) {
        this.frequenciesOffset = frequenciesOffset;
    }

    public long getFrequenciesOffset() {
        return frequenciesOffset;
    }

    public int getDocIdsSize() {
        return docIdsSize;
    }

    public void setDocIdsSize(int docIdsSize) {
        this.docIdsSize = docIdsSize;
    }

    public int getFrequenciesSize() {
        return frequenciesSize;
    }

    public void setFrequenciesSize(int frequenciesSize) {
        this.frequenciesSize = frequenciesSize;
    }

    public byte[] serializeBinary() {

        byte[] lexiconEntry = new byte[Constants.LEXICON_ENTRY_SIZE];
        //variable number of bytes
        byte[] entryTerm = term.getBytes(StandardCharsets.UTF_8);
        //fixed number of bytes, 4 for each integer, 8 for each long
        byte[] entryDf = Utils.intToByteArray(documentFrequency);
        byte[] entryCf = Utils.intToByteArray(collectionFrequency);
        byte[] entryDocIDOffset = Utils.longToByteArray(docIdsOffset);
        byte[] entryFrequenciesOffset = Utils.longToByteArray(frequenciesOffset);
        byte[] entryDocIDSize = Utils.intToByteArray(docIdsSize);
        byte[] entryFrequenciesSize = Utils.intToByteArray(frequenciesSize);

        //fill the first part of the buffer with the utf-8 representation of the term, leave the rest to 0
        System.arraycopy(entryTerm, 0, lexiconEntry, 0, entryTerm.length);
        //fill the last part of the buffer with statistics and offsets
        System.arraycopy(entryDf, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 32, 4);
        System.arraycopy(entryCf, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 28, 4);
        System.arraycopy(entryDocIDOffset, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 24, 8);
        System.arraycopy(entryFrequenciesOffset, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 16, 8);
        System.arraycopy(entryDocIDSize, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 8, 4);
        System.arraycopy(entryFrequenciesSize, 0, lexiconEntry, Constants.LEXICON_ENTRY_SIZE - 4, 4);
        return lexiconEntry;
    }

    public void deserializeBinary(byte[] buffer) {
        //to decode the term, detect the position of the first byte equal 0
        int endOfString = 0;
        while(buffer[endOfString] != 0){
            endOfString++;
        }
        //parse only the first part of the buffer until the first byte equal 0
        term = new String(buffer, 0, endOfString, StandardCharsets.UTF_8);
        //decode the rest of the buffer
        documentFrequency = Utils.byteArrayToInt(buffer, Constants.LEXICON_ENTRY_SIZE - 32);
        collectionFrequency = Utils.byteArrayToInt(buffer, Constants.LEXICON_ENTRY_SIZE - 28);
        docIdsOffset = Utils.byteArrayToLong(buffer, Constants.LEXICON_ENTRY_SIZE - 24);
        frequenciesOffset = Utils.byteArrayToLong(buffer, Constants.LEXICON_ENTRY_SIZE - 16);
        docIdsSize = Utils.byteArrayToInt(buffer, Constants.LEXICON_ENTRY_SIZE - 8);
        frequenciesSize = Utils.byteArrayToInt(buffer, Constants.LEXICON_ENTRY_SIZE - 4);
    }

    public String[] serializeToString() {
        ArrayList<String> list = new ArrayList<>();
        list.add(term);
        list.add(Integer.toString(documentFrequency));
        list.add(Integer.toString(collectionFrequency));
        return list.toArray(new String[0]);
    }

    public void deserializeFromString(String buffer) {
        List<String> elements = Arrays.asList(buffer.split(","));
        term = elements.get(0);
        documentFrequency = Integer.parseInt(elements.get(1));
        collectionFrequency = Integer.parseInt(elements.get(2));
    }
}
