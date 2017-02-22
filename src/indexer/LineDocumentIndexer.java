/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.util.Version;

/**
 *
 * @author Debasis
 */
public class LineDocumentIndexer extends RealValuedVecIndexer {
    Analyzer analyzer;
    
    static final String FIELD_WORDS = "words";
    
    public LineDocumentIndexer(String propFile, String indexName) throws Exception {
        super(propFile, indexName);
        analyzer = constructAnalyzer();        
    }
    
    Analyzer constructAnalyzer() {
        Analyzer eanalyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(buildStopwordList("stopfile"))); // default analyzer
        return eanalyzer;        
    }
    
    protected List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try (FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }
    
    Document constructDoc(FileWriter fw, String id, String line) throws Exception {
        
        Document doc = new Document();
        doc.add(new Field(DocVector.FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        StringBuffer tokenizedContentBuff = new StringBuffer();
        TokenStream stream = analyzer.tokenStream(FIELD_WORDS, new StringReader(line));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();

        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            tokenizedContentBuff.append(term).append(" ");
        }

        stream.end();
        stream.close();
        
        tokenizedContentBuff.append("\n");
        fw.write(id + "\t" + tokenizedContentBuff.toString());
        
        // Reanalyze
        doc.add(new Field(FIELD_WORDS, line,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        return doc;        
    }
    
    @Override
    void indexFile() throws Exception {
        File file = new File(prop.getProperty("dvec.file"));
        
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        FileWriter fw = new FileWriter(prop.getProperty("dump.file"));
        
        final int batchSize = 10000;
        int count = 0;
        // Each line is a tweet document
        while ((line = br.readLine()) != null) {
            
            String[] tokens = line.split("\\t");
            Document luceneDoc = constructDoc(fw, tokens[0], tokens[1]);
            
            if (count%batchSize == 0) {
                System.out.println("Added " + count + " documents...");
            }
            
            writer.addDocument(luceneDoc);
            count++;
        }
        br.close();
        fr.close();        
        fw.close();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java LineDocumentIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            LineDocumentIndexer lineDocIndexer = new LineDocumentIndexer(args[0], "index");
            lineDocIndexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
