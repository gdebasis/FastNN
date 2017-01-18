/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

/**
 *
 * @author Debasis
 */


public class SiftReader {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java SiftVecIndexer <prop-file>");
            args[0] = "init.properties";
        }

        try {
            VecQueries qvecs = new VecQueries(args[0]);
            System.out.println(qvecs);            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }        
    }    
}
