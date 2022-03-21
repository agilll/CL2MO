// class with methods to preprocess input xml files

import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.util.ArrayList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class Preprocess {

    // preprocess a file
    public static void doPreProcessFile (String file)
    {
        if (file.endsWith(".xml"))   {
            doEscapeAmp(file);
            doAddEntities(file);
            doChangeComillasInsideMapKey(file);
        }
    }

    // preprocess all files in a folder
    public static void doPreProcessFolder (String folder)
    {
        File fd = new File(folder);

        for (File fileEntry : fd.listFiles()) {
            String name = fileEntry.getName();

            if (fileEntry.isDirectory())
                Preprocess.doPreProcessFolder(folder+"/"+name);
            else
                Preprocess.doPreProcessFile(folder+"/"+name);
        }
    }


    // search a file to check if it contains & and escape to &amp;
    public static int doEscapeAmp (String fileName)
    {
       ArrayList<String> lines = new ArrayList<String>();
       String line;
       int amp=0;

       BufferedReader br = null;

       try {
           br = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.ISO_8859_1);

           // read all lines in file searching &nbsp;
           while ((line = br.readLine()) != null) {
              if (line.contains(" & "))  {
                 line = line.replace(" & ", " &amp; ");
                 amp=1;
              }

              lines.add(line);  // add line
           }

           br.close();
       }
       catch (IOException ex) {
           System.out.println("IOException reading file to add entities: "+fileName+" --> "+ex.toString());
           return -1;
       }


       Path path = Paths.get(fileName);
       BufferedWriter writer;

       if (amp==1) {  // contains " & ", need to store
           try {
              writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

              for (String s: lines)
                  writer.append(s+"\n");

              writer.close();
            }
            catch (IOException ex) {
              System.out.println("ERROR: IOException writing file to add entities: "+fileName);
              return -1;
            }

       } // fin de grabar

       return 0;
    }


    // search a file to check if it uses entities that must be added to the declaration
    public static int doAddEntities (String fileName)
    {
       ArrayList<String> lines = new ArrayList<String>();
       String line;
       int nbsp = 0;
       int doctype = 0;

       BufferedReader br = null;

       try {
           br = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.ISO_8859_1);

           // read all lines in file searching &nbsp;
           while ((line = br.readLine()) != null) {
              if (line.contains("&nbsp;"))  nbsp=1;  // it contains &nbsp;
              if (line.contains("<!DOCTYPE assessmentItem [ <!ENTITY nbsp"))  doctype=1;   // it already contain declaration for &nbsp;

              lines.add(line);  // add line
           }

           br.close();
       }
       catch (IOException ex) {
           System.out.println("IOException reading file to add entities: "+fileName+" --> "+ex.toString());
           return -1;
       }


       Path path = Paths.get(fileName);
       BufferedWriter writer;

       if ((nbsp==1) && (doctype == 0)) {  // contains &nbsp;  but not doctype yet
           lines.set(0, lines.get(0)+"\n<!DOCTYPE assessmentItem [ <!ENTITY nbsp \" \">]>\n");  // add declaration after root

           try {
              writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

              for (String s: lines)
                  writer.append(s+"\n");

              writer.close();
            }
            catch (IOException ex) {
              System.out.println("ERROR: IOException writing file to add entities: "+fileName);
              return -1;
            }

       } // fin de grabar

       return 0;
    }






    // to preprocess a file searching for a Claroline bug exporting mapKey getAttribute
    // if the value contains " they are not escaped inside the mapKey attribuite
    // this preprocess chages " by &apos; inside mapkey values
    public static void doChangeComillasInsideMapKey (String fileName)
    {
      ArrayList<String> lines = new ArrayList<String>();
      String line;
      int someChange=0;

      BufferedReader br = null;

      try {
          br = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.ISO_8859_1);

          // read all lines in file, and change those containing " inside mapKey by &apos;
          while ((line = br.readLine()) != null) {

             // if no mapKey attribute, no problem
             int posMapKey = line.indexOf("mapKey");
             if (posMapKey == -1) {
                lines.add(line);
                continue;
             }

             // there is mapKey
             // format should be '  mapKey="value"  mappedValue="value"  '

             int posQuot = line.indexOf("\"", posMapKey);  // find position of " after 'mapKey'
             String beforeQuot = line.substring(0, posQuot+1);  // keep starting staring till " included

             int posMappedValue = line.indexOf("mappedValue", posQuot);  // find mappedValue position
             if (posMappedValue == -1) posMappedValue = line.length() -1; // no mappedValue, it is in the following line, we keep alll the line

             int posLastQuot = line.lastIndexOf("\"", posMappedValue); // find position of " closing mapKey
             String afterQuot = line.substring(posLastQuot-1);    // keep ending string from " to the end

             String value = line.substring(1+posQuot, posLastQuot-1);  // extract mapKey value

             posQuot = value.indexOf("\"");  // search for " inside value
             if (posQuot != -1) {   // no " no problem
                value = value.replace("\"", "&apos;");  // if ", change by  &apos;
                line = beforeQuot+value+afterQuot;  // rebuild the line
                someChange = 1;  // flag change in line
             }

             lines.add(line);  // add the line
         }

         br.close();
      }
      catch(Exception ex) {
           System.out.println("Exception reading file to change \": "+fileName+" --> "+ex.toString());
      }


      // save the file if something changed
      if (someChange == 1) {

            Path path = Paths.get(fileName);
            BufferedWriter writer;

            try {
                writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

                for (String s: lines)
                    writer.append(s+"\n");

                writer.close();
            }
            catch (IOException ex) {
               System.out.println("IOException writing the file to change \": "+fileName+" --> "+ex.toString());
               System.exit(-1);
            }
      }
    }  // close doChangeComillasInsideMapKeyInFile

}  // close the class
