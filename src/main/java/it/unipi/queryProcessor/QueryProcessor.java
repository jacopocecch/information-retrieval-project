package it.unipi.queryProcessor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unipi.exceptions.IllegalQueryTypeException;
import it.unipi.exceptions.TermNotFoundException;
import it.unipi.exceptions.TerminatedListException;
import it.unipi.models.*;
import it.unipi.utils.Constants;
import it.unipi.utils.Utils;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class QueryProcessor {
    private final String[] QUIT_CODES = new String[]{"Q", "q", "QUIT", "quit", "EXIT", "exit"};

    private final LoadingCache<Integer, Document> documentTableCache = CacheBuilder.newBuilder()
            .maximumSize(1_000)
            .build(new CacheLoader<>() {
                @Nonnull
                public Document load(@Nonnull Integer docId) {
                    // Theoretically we should always find the doc entry, so this could be useless
                    Document document = docTableDiskSearch(docId);
                    if (document == null) {
                        throw new RuntimeException();
                    }
                    return document;
                }
            });

    private final LoadingCache<String, LexiconTerm> lexiconCache = CacheBuilder.newBuilder()
            .maximumSize(1_000)
            .build(new CacheLoader<>() {
                @Nonnull
                public LexiconTerm load(@Nonnull String term) throws TermNotFoundException {
                    LexiconTerm lexiconTerm = lexiconDiskSearch(term);
                    // query term may not exist in our Lexicon
                    if (lexiconTerm == null) {
                        throw new TermNotFoundException();
                    }
                    return lexiconTerm;
                }
            });
    private final CollectionStatistics collectionStatistics;

    private final SortedSet<DocumentScore> docsPriorityQueue;

    private final FileChannel lexiconChannel;
    private final FileChannel docTableChannel;

    private int k;
    private long startQuery;

    public QueryProcessor() throws IOException{
        // loading collection statistics
        collectionStatistics = new CollectionStatistics();
        try (FileInputStream fisCollectionStatistics = new FileInputStream(Constants.COLLECTION_STATISTICS_FILE_PATH + Constants.DAT_FORMAT)){
            byte[] csBytes = fisCollectionStatistics.readNBytes(16);
            collectionStatistics.deserializeBinary(csBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        k = 10;
        docsPriorityQueue = new TreeSet<>();
        lexiconChannel = FileChannel.open(Paths.get(Constants.LEXICON_FILE_PATH + Constants.DAT_FORMAT));
        docTableChannel = FileChannel.open(Paths.get(Constants.DOCUMENT_TABLE_FILE_PATH + Constants.DAT_FORMAT));
    }

    public void commandLine(){
        System.out.println("Starting the command line..");
        System.out.println("Input Format: [AND|OR] term1 ... termN");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            System.out.print("> ");
            while ((line = in.readLine()) != null) {
                startQuery = System.currentTimeMillis();
                if(Arrays.asList(QUIT_CODES).contains(line)){
                    System.out.println("Shutting down...");
                    break;
                }
                Boolean success = false;
                try {
                    success = processQuery(line);
                } catch(IllegalQueryTypeException e){
                    e.printStackTrace();
                    System.out.println("Input Format: [AND|OR] term1 ... termN");
                } catch (ExecutionException | TerminatedListException e) {
                    throw new RuntimeException(e);
                }
                if(success)
                    returnResults();
                    docsPriorityQueue.clear();
                long end = System.currentTimeMillis();
                System.out.println(((double)(end - startQuery)/1000) + " seconds");
                System.out.print("> ");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean processQuery(String query) throws IllegalQueryTypeException, IOException, ExecutionException, TerminatedListException {

        String[] tokens = Utils.tokenize(query);

        String queryType = tokens[0];
        HashSet<String> tokensSet = new HashSet<>();
        for (int i = 1; i < tokens.length; ++i){
            // skip first token specifying the type of the query
            if(Utils.isAStopWord(tokens[i])) continue; // also remove stopwords
            tokens[i] = Utils.truncateToken(tokens[i]);
            tokens[i] = Utils.stemToken(tokens[i]);
            tokensSet.add(tokens[i]);
        }
        // TODO Maybe change set to list for postingLists?
        Set<PostingListInterface> postingLists = loadPostingLists(tokensSet);

        postingLists.removeIf(postingList -> !postingList.next());

        int currentDocId;

        OptionalInt currentDocIdOpt;

        if ((currentDocIdOpt = postingLists.stream()
                .mapToInt(PostingListInterface::getDocId)
                .min()).isEmpty()) {
            return false;
        } else {
            currentDocId = currentDocIdOpt.getAsInt();
        }

        while (!postingLists.isEmpty()) {
            double score = BM25Scorer(currentDocId, postingLists);
            DocumentScore docScore = new DocumentScore(currentDocId, score);

            if (docsPriorityQueue.size() < k) {
                docsPriorityQueue.add(docScore);
            } else {
                if (score > docsPriorityQueue.last().score()) {
                    docsPriorityQueue.add(docScore);
                    docsPriorityQueue.remove(docsPriorityQueue.last());
                }
            }

            if ((currentDocIdOpt = postingLists.stream()
                    .mapToInt(PostingListInterface::getDocId)
                    .min()).isEmpty()) {
                //posting lists are finished
                return true;
            } else {
                currentDocId = currentDocIdOpt.getAsInt();
            }
        }

        /*
        if(queryType.equals("and")) {
            System.out.println("You have requested a conjunctive query with the following preprocessed tokens:");
            printTokens(Arrays.copyOfRange(tokens, 1, tokens.length));
            //result = conjunctiveQuery(postingLists);
        } else if (queryType.equals("or")) {
            System.out.println("You have requested a disjunctive query with the following preprocessed tokens:");
            printTokens(Arrays.copyOfRange(tokens, 1, tokens.length));
            //result = disjunctiveQuery(postingLists);
        }
        else throw new IllegalQueryTypeException(tokens[0]);

         */
        return false;
    }

    private double BM25Scorer(int currentDocId, Set<PostingListInterface> postingLists) throws ExecutionException {
        double score = 0;
        Document currentDoc = docTableDiskSearch(currentDocId);
        for (Iterator<PostingListInterface> postingListIterator = postingLists.iterator(); postingListIterator.hasNext();) {
            PostingListInterface postingList = postingListIterator.next();
            if (postingList.getDocId() != currentDocId) continue;
            int termFreq = postingList.getFreq();
            int docFreq = lexiconCache.get(postingList.getTerm()).getDocumentFrequency();
            // TODO I think we should compute it during indexing and save it in CollectionStatistics, also IDF?
            double avgDocLen = (double)collectionStatistics.getNumTotalTerms() / (double)collectionStatistics.getNumDocs();
            // compute partial score
            score += ((double) termFreq / ((1 - Constants.B_BM25) + Constants.B_BM25 * ( (double) currentDoc.getLength() / avgDocLen))) * Math.log((double) collectionStatistics.getNumDocs() / docFreq);
            // posting list end
            if (!postingList.next()) postingListIterator.remove();
        }
        return score;
    }

    private void returnResults() {
        for(DocumentScore ds : docsPriorityQueue){
            System.out.println(ds);
        }
    }


    private void printTokens(String[] tokens){
        for(int i = 0; i < tokens.length; ++i)
            if(i < tokens.length-1)
                System.out.print(tokens[i] + ", ");
            else
                System.out.print(tokens[i]);
        System.out.println();
    }

    public void conjunctiveQuery(PostingListInterface[] postingLists){

    }

    public void disjunctiveQuery(PostingListInterface[] postingLists){
    }

    public Set<PostingListInterface> loadPostingLists(Set<String> tokens) throws IOException {
        HashSet<PostingListInterface> postingLists = new HashSet<>();
        for (String token : tokens) {
            if(Utils.isAStopWord(token)){
                continue;
            }
            LexiconTerm lexiconTerm;
            try {
                lexiconTerm = lexiconCache.get(token);
            } catch (ExecutionException e) {
                e.printStackTrace();
                continue;
            }
            PostingListInterface pl = new PostingListInterface(lexiconTerm);
            postingLists.add(pl);
        }
        return postingLists;
    }

    private LexiconTerm lexiconDiskSearch(String term) {
        try {
            long fileSeekPointer;
            int numberOfTerms = (int)lexiconChannel.size() / Constants.LEXICON_ENTRY_SIZE;

            LexiconTerm currentEntry = new LexiconTerm();
            ByteBuffer buffer = ByteBuffer.allocate(Constants.LEXICON_ENTRY_SIZE);
            int leftExtreme = 0;
            int rightExtreme = numberOfTerms;

            while(rightExtreme > leftExtreme){
                fileSeekPointer = (long) (leftExtreme + ((rightExtreme - leftExtreme) / 2)) * Constants.LEXICON_ENTRY_SIZE;
                lexiconChannel.position(fileSeekPointer);
                buffer.clear();
                lexiconChannel.read(buffer);
                String currentTerm = currentEntry.deserializeTerm(buffer.array());
                if(currentTerm.compareTo(term) > 0){
                    //we go left on the array
                    rightExtreme = rightExtreme - (int)Math.ceil(((double)(rightExtreme - leftExtreme) / 2));
                } else if (currentTerm.compareTo(term) < 0) {
                    //we go right on the array
                    leftExtreme = leftExtreme + (int)Math.ceil(((double)(rightExtreme - leftExtreme) / 2));
                } else {
                    currentEntry.deserializeBinary(buffer.array());
                    return currentEntry;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Document docTableDiskSearch(int docId) {
        // TODO doc_lens seem unrealistic?
        Document doc = new Document();
        try{
            long fileSeekPointer = (long) docId * Constants.DOCUMENT_ENTRY_SIZE;
            docTableChannel.position(fileSeekPointer);
            ByteBuffer buffer = ByteBuffer.allocate(Constants.DOCUMENT_ENTRY_SIZE);
            docTableChannel.read(buffer);
            doc.deserializeBinary(buffer.array());
        }catch (IOException e) {
            e.printStackTrace();
        }
        return doc;
    }
}