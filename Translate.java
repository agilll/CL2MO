// program to translate a Claroline question (or a folder with several ones) to Moodle format
// in case a parent folder, an aggregated file is created to import in Moodle and a report is generated with warnings

import javax.xml.parsers.*;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.xml.sax.*;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;


public class Translate {

    static ArrayList<String> report = new ArrayList<String>();  // to store warnings to be print at the end

    static DocumentBuilderFactory dbf;
    static DocumentBuilder db;

    // entry point
    public static void main (String[] args)
    {
       String input, reportFilename=null;
       int result;

       if (args.length != 1) {
         System.out.println("A parameter is required (the object to translate, file or folder)");
         System.exit(0);
       }

       input = args[0]; // get the parameter: the object to translate

       // create the parser
       dbf = DocumentBuilderFactory.newInstance();

       try {
          db = dbf.newDocumentBuilder();
       }
       catch (ParserConfigurationException ex) {
         System.out.println("ParserConfigurationException creating the parser");
         System.exit(-1);
       }


       File fd = new File(input);

       if (fd.isDirectory()) {  // a folder
            if (input.endsWith("/"))
              input = input.substring(0,input.length()-1);   // remove the last '/'

            Preprocess.doPreProcessFolder(input);  // preprocess the folder
            Translate.doProcessFolder(input, "");  // translate the folder contents

            System.out.println("\nTranslation finished");

            if (input.contains("/")) {
              System.out.println("\nNo aggregation file is created if input is a subfolder");
            }
            else {
              result = Translate.doBuildAgreggated(input);
              if (result != 0)
                  System.out.println("Aggregated file could bot be created\n");
              else
                  System.out.println("Aggreated file successfully created\n");

              reportFilename = "uvigo_translated_"+input+"_report.txt";  // a report is created only when a ggregated is generated
              result = Translate.saveReport(reportFilename);
            }
       }
       else {   // a simple file
            Preprocess.doPreProcessFile(input);   // preprocess the file
            result = Translate.doProcessFile(input, "");  // translate the file
            if (result == 0) System.out.println("\nTranslation finished");
            else System.out.println("\nTranslation could not be completed\n");
      }

   }



   // to save the report with warnings
   public static int saveReport (String reportFilename)
   {
     FileWriter fw = null;
     PrintWriter pw = null;

     if (reportFilename != null)  {  // the report is only created if the process finished correctly
         try {
            fw = new FileWriter(reportFilename);
            pw = new PrintWriter(fw);

            for (String s: report)   // report is a class var, a string List
              pw.println(s);

            fw.close();
         }
         catch (IOException ex) {
           System.out.println("IOException creating report file: "+reportFilename);
           System.exit(-1);
         }
     }

     return 0;

   }


   // Translate a folder
   public static void doProcessFolder (String folder, String tab)
   {
       System.out.println(tab+"############### Processing folder: "+folder);

       File fd = new File(folder);

       for (File fileEntry : fd.listFiles()) {  // read folder contents
           String name = fileEntry.getName();

           if (fileEntry.isDirectory()) {  // if a subfolder, repeat recursively
               Translate.doProcessFolder(folder+"/"+name, tab+"    ");
           }
           else {
               if (name.endsWith(".xml"))   { // if an XML file, translate the file
                  Translate.doProcessFile(folder+"/"+name, tab+"    ");
               }
           }
       }
   }


   // create aggregate file
   public static int doBuildAgreggated (String folder)
   {
       System.out.println("\nCreating aggregated file for "+folder);
       String outputFileName = "uvigo_translated_"+folder+".xml";

       ArrayList<String> resultLines = new ArrayList<String>();

       resultLines = Translate.addQuestions(folder);

       Path path = Paths.get(outputFileName);
       BufferedWriter writer;

       try {
          writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);
        }
        catch (IOException ex) {
          System.out.println("ERROR: IOException opening output file: "+outputFileName);
          Translate.report.add("Could not open output file to save translation: '"+outputFileName+"'");
          return -1;
        }

        try {
          writer.append("<?xml version='1.0' encoding='iso_8859_1'?>\n");
          writer.append("<quiz>\n");

          for (String line: resultLines)
              writer.append(line+"\n");

          writer.append("</quiz>");
        }
        catch (IOException ex) {
          System.out.println("ERROR: IOException writing output file: "+outputFileName+" --> "+ex.toString());
          Translate.report.add("Translation could not be saved: '"+outputFileName+"'");
          return -1;
        }

       try {
          writer.close();
       }
       catch (IOException ex) {
         System.out.println("ERROR: IOException closing output file: "+outputFileName+" --> "+ex.toString());
         Translate.report.add("Could not close output file to save translation: '"+outputFileName+"'");
         return -1;
       }

       return 0;
     }



    // read all the lines of the XML files starting with 'uvigo_translated_' in this folder, and search recursively in subfolders
    public static  ArrayList<String> addQuestions (String folder)
    {
       ArrayList<String> resultLines = new ArrayList<String>();
       File fd = new File(folder);

       // read all the entries
       for (File fileEntry : fd.listFiles()) {
           String name = fileEntry.getName();

           if (fileEntry.isDirectory()) {  // a subfolder, entry recursively
               resultLines.addAll(Translate.addQuestions(folder+"/"+name));
           }
           else { // a file, it will be aggregated if it is an XMl file starting with 'uvigo_translated_'
               if ( name.endsWith(".xml") && (name.startsWith("uvigo_translated_") ) )  {
                  String fileName = folder+"/"+name;

                  try {  // read the file lines
                      BufferedReader br = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.ISO_8859_1);

             		      String line;
                      int counter =0;
             		      while ((line = br.readLine()) != null) {
                          String trimmedLine = line.trim();
                          counter++;
                          if (   ((counter == 1) && (trimmedLine.startsWith("<?xml")))   // don't add the xml declaration
                              || ((counter == 2) && (trimmedLine.startsWith("<quiz>")))  // and the <quiz> wrapper
                              || (trimmedLine.startsWith("</quiz>"))
                             )
                             continue;
             		    	    resultLines.add(line);
             		      }

             	        br.close();
             	    }
             	    catch(Exception e) {
             	       System.out.println("Exception reading file: "+ fileName + ": " + e);
                     Translate.report.add("Could not add file: "+folder+"/"+name+" to aggregated result");
             	    }
               }
           }
       }

       return resultLines;
   }




   // translate a file
   public static int doProcessFile (String inputFileName, String tab)
   {
       Document doc = null;
       NodeList nl;
       Element assessmentItem;
       int result;
       String identifier, title, inputFolder, outputFileName;

       System.out.print(tab+"***** Processing file: "+inputFileName);

       // get the input folder for this file
       int lastSlash = inputFileName.lastIndexOf('/');
       if (lastSlash == -1)
          inputFolder = "./";
       else
          inputFolder = inputFileName.substring(0, 1+lastSlash);

       // parse the file and get the DOM document
       try {
          FileInputStream fip = new FileInputStream(inputFileName);
          doc = db.parse(fip);
        }
        catch (IOException ex) {
          System.out.println("\n"+tab+"IOException reading input file: "+inputFileName);
          Translate.report.add("Input file could not be read: "+inputFileName);
          return -1;
        }
        catch (SAXException ex) {
          System.out.println("\n"+tab+"SAXException (not a well-formed file) reading the input file: "+inputFileName+": "+ex.toString());
          Translate.report.add("Input file could not be analysed (not well formed): "+inputFileName);
          return -1;
        }
        catch (Exception ex) {
          System.out.println("\n"+tab+"Exception reading input file: "+inputFileName);
          Translate.report.add("Input file could not be analysed (unknown cause(): "+inputFileName);
          return -1;
        }


        // file was succesfully read
        // get its encoding

        String encoding = doc.getXmlEncoding();
        if (encoding == null) {
          System.out.println("\n"+tab+"---------- File encoding not available");
          return -1;
        }

       // get the root element, it should be  <assessmentItem>
       assessmentItem = doc.getDocumentElement();
       if (!assessmentItem.getNodeName().equals("assessmentItem")) {
         System.out.println("\n"+tab+"---------- Document root is not <assessmentItem>. It is not a Claroline question");
         return -1;
       }

       // get its attribute 'identifier', used to build the output file name
       identifier = assessmentItem.getAttribute("identifier");
       if (identifier == null) {
         System.out.println("\n"+tab+"---------- Error: <assessmentItem> has no attribute 'identifier'");
         Translate.report.add("Incorrect file: "+inputFileName+" (<assessmentItem> has no attribute 'identifier')");
         return -1;
       }

       // get its attribute  'title', the name of the question
       title = assessmentItem.getAttribute("title");
       if (title == null) {
         System.out.println("\n"+tab+"---------- Error: <assessmentItem> has no attribute 'title'");
         Translate.report.add("Incorrect file: "+inputFileName+" (<assessmentItem> has no attribute 'title')");
         return -1;
       }

       // title = title.replace("&", "&amp;");  // change the entity & if present

       // create the output file name
       outputFileName = inputFolder+"uvigo_translated_"+identifier+".xml";


       // now let's study the question contents to find out its type

       // check choiceInteraction - multiple choice questions, with single or multiple answer, including T/F questions
       nl = assessmentItem.getElementsByTagName("choiceInteraction");
       if (nl.getLength() > 0) {
          System.out.println("  --> Multiple choice question");
          result = TranslateMultipleChoice.doMultipleChoice(outputFileName, inputFolder, encoding, title, assessmentItem);
          if (result != 0) {
             System.out.println(tab+"---------- Problem translating question: "+title);
             Translate.report.add("Question could not be translated: "+inputFileName);
          }
          return result;
       }
       else {
          // check inlineChoiceInteraction - question to fill holes choosing from a list
          nl = assessmentItem.getElementsByTagName("inlineChoiceInteraction");
          if (nl.getLength() > 0) {
             System.out.println("  --> Question to fill holes choosing from a list");
             result = TranslateInlineChoice.doInlineChoice(outputFileName, inputFolder, encoding, title, assessmentItem);
             if (result != 0) {
                System.out.println(tab+"---------- Problem translating question");
                Translate.report.add("Question could not be translated: "+inputFileName);
             }
             return result;
          }
          else {
             // check textEntryInteraction - question to fill holes writing
             nl = assessmentItem.getElementsByTagName("textEntryInteraction");
             if (nl.getLength() > 0) {
                System.out.println("  --> Question to fill holes writing");
                result = TranslateEntryInteraction.doEntryInteraction(outputFileName, inputFolder, encoding, title, assessmentItem);
                if (result != 0) {
                   System.out.println(tab+"---------- Problem translating question");
                   Translate.report.add("Question could not be translated: "+inputFileName);
                }
                return result;
             }
             else {
                // check matchInteraction - question to relate
                nl = assessmentItem.getElementsByTagName("matchInteraction");
                if (nl.getLength() > 0) {
                   System.out.println("  --> Relation question");
                   result = TranslateMatchInteraction.doMatchInteraction(outputFileName, inputFolder, encoding, title, assessmentItem);
                   if (result != 0) {
                      System.out.println(tab+"---------- Problem translating question");
                      Translate.report.add("Question could not be translated: "+inputFileName);
                   }
                   return result;
                }
                else {
                   System.out.println(tab+"---------- Unknown type question: "+title);
                   Translate.report.add("Question could not be translated: "+inputFileName);
                   return -1;
                }
             }
          }
      }
   }  // close doProcessFile

}  // close class
