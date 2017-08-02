
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package txtvecnn;

import approxnn.ANNList;
import approxnn.ANNRetriever;
import indexer.DocVector;
import java.util.*;
import java.io.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author Debasis
 */
public class TextNNRetriever extends ANNRetriever {

    public TextNNRetriever(String propFile) throws Exception {
        super(propFile);
    }

    @Override
    public void searchWithBenchmarkQueries() throws Exception {
        List<DocVector> queries = getQueries();
        int numQueries = queries.size();
        int span = Integer.parseInt(prop.getProperty("match.span", "1"));
        int ntop = Integer.parseInt(prop.getProperty("docs.nwanted", "1000"));
                FileWriter fw = new FileWriter(prop.getProperty("res.file"));
                BufferedWriter bw = new BufferedWriter(fw);

        if (end == -1) {
            end = numQueries;
        }
        end = Math.min(end, numQueries);

        for (int i = start; i < end; i++) {
            DocVector qvec = queries.get(i);

            System.out.println("Retrieving for query: " + qvec);
            ANNList anns = subSpaceDimension == 0 ? retrieve(qvec, span) : retrieveFixedProjections(qvec, span);

            List<DocVector> retrDocvecs = anns.selectTopK(qvec, reader, ntop);

            int rank=1;
            for (DocVector dvec : retrDocvecs) {
                float annDist = (float) Math.sqrt(dvec.getDistFromQuery());
                String docName = dvec.getDocName();
                bw.write((i+1) + "\tQ0\t" +
                        docName + "\t" + rank + "\t" + (-1*annDist));
                                bw.newLine();
                rank++;
            }
        }
                bw.close();
                fw.close();
    }

    String getDocumentName(int docId) throws Exception {
        TermQuery tq = new TermQuery(new Term(DocVector.FIELD_ID, String.valueOf(docId)));
        TopDocs hits = searcher.search(tq, 1);
        Document d = reader.document(hits.scoreDocs[0].doc);
        return d.get(DocVector.FIELD_ID);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java TextNNRetriever <prop-file>");
            args[0] = "init_synthetic.properties";
        }

        try {
            TextNNRetriever textRetriever = new TextNNRetriever(args[0]);
            textRetriever.searchWithBenchmarkQueries();
            textRetriever.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
