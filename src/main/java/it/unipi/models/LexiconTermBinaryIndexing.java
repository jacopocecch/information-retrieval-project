package it.unipi.models;

import it.unipi.utils.Constants;
import it.unipi.utils.DiskDataStructuresSearch;
import it.unipi.utils.EncodingUtils;
import it.unipi.utils.ScoringFunctions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.MappedByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class LexiconTermBinaryIndexing extends LexiconTermIndexing {

    //encoded posting_list used for performance during merge
    private byte[] encodedDocIDs;
    private byte[] encodedFrequencies;
    //used to keep file pointers during merge
    static private int docIDsFileOffset = 0;
    static private int frequenciesFileOffset = 0;

    public LexiconTermBinaryIndexing() {
    }

    public LexiconTermBinaryIndexing(String term) {
        super(term);
    }

    public byte[] serialize() {
        return this.serializeBinary();
    }


    //decode a disk-based array of bytes representing a lexicon entry in a LexiconTermIndexing object
    public void deserialize(byte[] buffer) {
        deserializeBinary(buffer);
    }

    public void mergeEncodedPostings(byte[] encodedDocIDs, byte[] encodedFrequencies){
        //get current encoded partial posting list and extend with additional posting
        if(this.encodedDocIDs == null){
            this.encodedDocIDs = encodedDocIDs;
            this.encodedFrequencies = encodedFrequencies;
        } else {
            //doc_ids
            byte[] mergedDocIDsArray = new byte[this.encodedDocIDs.length + encodedDocIDs.length];
            //fill the first part of the array with the array representing previous encoded posting list
            System.arraycopy(this.encodedDocIDs, 0, mergedDocIDsArray, 0, this.encodedDocIDs.length);
            //fill the second part of the array with the array representing the newly added posting list
            System.arraycopy(encodedDocIDs, 0, mergedDocIDsArray, this.encodedDocIDs.length, encodedDocIDs.length);
            this.encodedDocIDs = mergedDocIDsArray;

            //frequencies, the process is the same as for docIds
            byte[] mergedFrequenciesArray = new byte[this.encodedFrequencies.length + encodedFrequencies.length];
            System.arraycopy(this.encodedFrequencies, 0, mergedFrequenciesArray, 0, this.encodedFrequencies.length);
            System.arraycopy(encodedFrequencies, 0, mergedFrequenciesArray, this.encodedFrequencies.length, encodedFrequencies.length);
            this.encodedFrequencies = mergedFrequenciesArray;
        }
    }

    public void computeStatistics(MappedByteBuffer docTableBuffer, CollectionStatistics collectionStatistics){
        //decode posting list
        this.setPostingListDocIds(EncodingUtils.decode(this.encodedDocIDs));
        this.setPostingListFrequencies(EncodingUtils.decode(this.encodedFrequencies));
        //compute termUpperBound and collection frequency
        this.termUpperBound = -1;
        int i = 0;
        int collectionFrequency = 0;
        for(Integer docID: this.postingListDocIds){
            Document d = DiskDataStructuresSearch.docTableDiskSearch(docID, docTableBuffer);
            double score = ScoringFunctions.BM25(d.getLength(), this.postingListFrequencies.get(i), this, collectionStatistics);
            //double score = ScoringFunctions.TFIDF(this.postingListFrequencies.get(i), this, collectionStatistics);
            if (score > this.termUpperBound){
                this.termUpperBound = score;
            }
            collectionFrequency = collectionFrequency + this.postingListFrequencies.get(i);
            i++;
        }

        //compute cf
        this.setCollectionFrequency(collectionFrequency);
    }

    public void writeToDisk(OutputStream docIDStream, OutputStream frequenciesStream, OutputStream lexiconStream) throws IOException {
        int numSkipBlocks;
        int blockSize;

        //(doc id,offset) list for skip pointers
        LinkedHashMap<Integer, SkipPointerEntry> skipPointers = new LinkedHashMap<>();

        //decode posting lists
        this.setPostingListDocIds(EncodingUtils.decode(this.encodedDocIDs));
        this.setPostingListFrequencies(EncodingUtils.decode(this.encodedFrequencies));

        //set document frequency
        this.documentFrequency = this.postingListDocIds.size();

        //if the posting list is long, create skip pointers to be used for nextGEQ implementation
        if (this.documentFrequency > Constants.SKIP_POINTERS_THRESHOLD) {

            //create sqrt(df) blocks of sqrt(df) size (rounded to the highest value when needed)
            blockSize = (int) Math.ceil(Math.sqrt(this.documentFrequency));
            numSkipBlocks = (int) Math.ceil((double)this.documentFrequency / (double)blockSize);

            long docIdOffset = 0;
            long frequencyOffset = 0;

            //Avoid inserting details about the last block, since they can be inferred from the previous ones
            for (int i = 0; i < numSkipBlocks - 1; i++) {
                // get first docID after the block
                int docId = postingListDocIds.get(blockSize * (i + 1));
                // in subList from is inclusive, to is exclusive
                docIdOffset += EncodingUtils.getEncodingLength(this.getPostingListDocIds().subList((i * blockSize) , ((i + 1) * blockSize)));
                frequencyOffset += EncodingUtils.getEncodingLength(this.getPostingListFrequencies().subList((i * blockSize), ((i + 1) * blockSize)));
                skipPointers.put(docId, new SkipPointerEntry(docIdOffset, frequencyOffset));
            }
        }

        //set inverted file offsets for this term
        this.setDocIdsOffset(docIDsFileOffset);
        this.setFrequenciesOffset(frequenciesFileOffset);

        if (skipPointers.size() > 0) {
            //docId (4 bytes) + offset (8 bytes) for each skip pointer in docId file
            byte[] skipPointersBytesDocIds = new byte[skipPointers.size() * 12];
            //offset (8 bytes) for each skip pointer in frequency file
            byte[] skipPointersBytesFrequency = new byte[skipPointers.size() * 8];
            int i = 0;
            for (Map.Entry<Integer, SkipPointerEntry> skipPointer: skipPointers.entrySet()) {
                System.arraycopy(EncodingUtils.intToByteArray(skipPointer.getKey()), 0, skipPointersBytesDocIds, i * 12, 4);
                System.arraycopy(EncodingUtils.longToByteArray(skipPointer.getValue().docIdOffset()), 0, skipPointersBytesDocIds, (i * 12) + 4, 8);
                System.arraycopy(EncodingUtils.longToByteArray(skipPointer.getValue().freqOffset()), 0, skipPointersBytesFrequency, i * 8, 8);
                i++;
            }
            docIDsFileOffset += skipPointersBytesDocIds.length;
            docIdsSize += skipPointersBytesDocIds.length;
            frequenciesFileOffset += skipPointersBytesFrequency.length;
            frequenciesSize += skipPointersBytesFrequency.length;
            docIDStream.write(skipPointersBytesDocIds);
            frequenciesStream.write(skipPointersBytesFrequency);
        }

        //update general file docId offset and docid size
        docIDsFileOffset += this.encodedDocIDs.length;
        docIdsSize += this.encodedDocIDs.length;
        docIDStream.write(this.encodedDocIDs);

        //update general file frequency offset and frequency size
        frequenciesFileOffset += this.encodedFrequencies.length;
        frequenciesSize += this.encodedFrequencies.length;
        frequenciesStream.write(this.encodedFrequencies);

        // lexicon
        lexiconStream.write(this.serialize());
    }


}
