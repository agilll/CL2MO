// class to translate several type of questions
// MultipleChoice, with simple or multiple answers
// True or False (it is a simulated MultipleChoice )

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import javafx.util.Pair;
import java.io.IOException;

import java.util.Locale;

import javax.xml.parsers.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.*;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class TranslateMultipleChoice {

    public static int doMultipleChoice (String outputFileName, String inputFolder, String encoding, String title, Element assessmentItem)
    {
       ArrayList<String> outputfileLines = new ArrayList<String>();

       Node aNode;
       NodeList nodeList, nodeList2;
       Element responseDeclaration, itemBody, simpleChoice, mapEntry;
       String cardinality, answer="", identifier, grade="";

       // mappingsGrades maps each answer to its penalty
       ArrayList<Pair<String,String>> mappingsGrades = new ArrayList<Pair<String,String>>();

       nodeList = assessmentItem.getElementsByTagName("responseDeclaration");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <responseDeclaration> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <responseDeclaration> elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       responseDeclaration = (Element)nodeList.item(0);

       // el atributo cardinality nos dirá si es elección simple o múltiple
       cardinality = responseDeclaration.getAttribute("cardinality");
       if (cardinality == null) {
         System.out.println("WARNING: <responseDeclaration> has no attribute 'cardinality'. Default is 'multiple'");
         Translate.report.add(outputFileName+": Strange question: "+title+" (<responseDeclaration> has no attribute 'cardinality'. Default is 'multiple')");
         cardinality = "multiple";
       }
       if (cardinality.equals("single"))
         cardinality = "true";
       else
         cardinality = "false";

       // let's see penalties
       nodeList = assessmentItem.getElementsByTagName("mapEntry");
       for (int x=0; x < nodeList.getLength(); x++) {
          mapEntry = (Element)nodeList.item(x);

          identifier = mapEntry.getAttribute("mapKey");
          if (identifier == null) {
             System.out.println("ERROR: an element <mapEntry> has no attribute 'mapKey'");
             Translate.report.add("Wrong question: "+title+" (an element <mapEntry> has no attribute 'mapKey'");
             return -1;
          }

          grade = mapEntry.getAttribute("mappedValue");
          if (grade == null) {
             System.out.println("ERROR: an element <mapEntry> has no attribute 'mappedValue'");
             Translate.report.add("Wrong question: "+title+" (an element <mapEntry> has no attribute 'mappedValue'");
             return -1;
          }
          mappingsGrades.add(new Pair(identifier, grade));
       }

       // process grades to transform to percentages
       mappingsGrades = TranslateMultipleChoice.processGrades(mappingsGrades, cardinality);

       // let's see the question body
       nodeList = assessmentItem.getElementsByTagName("itemBody");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <itemBody> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <itemBody> elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       itemBody = (Element)nodeList.item(0);

       // search for the question statement, and complete the mappings
      Statement statement = new Statement();
      statement = TranslateMultipleChoice.getStatement(statement, inputFolder, title, itemBody);

      // wrong statement
      if (statement == null) return -1;

      // create the translated question

      outputfileLines.add("<?xml version='1.0' encoding='"+encoding+"'?>");
      outputfileLines.add("<quiz>");
      outputfileLines.add("<question type='multichoice'>");  // type multichoice
      outputfileLines.add("<name><text><![CDATA["+title+"]]></text></name>");

      outputfileLines.add("<questiontext format='html'>");
      outputfileLines.add("<text>");

      outputfileLines.add("<![CDATA[");
      String textStatement = statement.getTextStatement();

      // if no text statement, it is likely the text statement is the title
      // many teachers wrongly use the title as text statement, as Claroline presents the title before the text statement
      if (TranslateMultipleChoice.isEmpty(textStatement))
          outputfileLines.add(title);
      outputfileLines.add(textStatement);

      // add the <img> links
      ArrayList<String> imgs = statement.getImgs();
      for (String eimg: imgs) {
         outputfileLines.add(eimg);
      }
      outputfileLines.add("]]>");

      outputfileLines.add("</text>");

      // add the images data
      ArrayList<Pair<String,String>> images = statement.getImages();

      for (Pair<String,String> p: images) {
         outputfileLines.add("<file name=\""+p.getKey()+"\" path=\"/\" encoding=\"base64\">"+p.getValue()+"</file>");
      }

      outputfileLines.add("</questiontext>");

      outputfileLines.add("<defaultgrade>1</defaultgrade>");
      outputfileLines.add("<single>"+cardinality+"</single>");

      outputfileLines.add("<shuffleanswers>true</shuffleanswers>");  // always shuffle the answers
      outputfileLines.add("<answernumbering>abc</answernumbering>"); // always select numbering abc
      outputfileLines.add("<showstandardinstruction>1</showstandardinstruction>");  // que diga lo de escoge una o varias

      // search and print the options
      nodeList = itemBody.getElementsByTagName("simpleChoice");
      if (nodeList.getLength() == 0) {
         System.out.println("ERROR: no elements <simpleChoice> with the options");
         Translate.report.add("Wrong question: "+title+" (no elements <simpleChoice> with the options)");
         return -1;
      }

      for (int x=0; x < nodeList.getLength(); x++) {
         simpleChoice = (Element)nodeList.item(x);

         identifier = simpleChoice.getAttribute("identifier");
         if (identifier == null) {
           System.out.println("ERROR: an element <simpleChoice> with an option has no attribute 'identifier'");
           Translate.report.add("Wrong question: "+title+" (an element <simpleChoice> with an option has no attribute 'identifier')");
           return -1;
         }

         // search for the penalty
         for (Pair <String,String> mapping : mappingsGrades)
         {
           if (mapping.getKey().equals(identifier)) {  // found
              grade = mapping.getValue();
              break;
           }
         }

         // print the option
         outputfileLines.add("<answer fraction='"+TranslateMultipleChoice.getValue(grade)+"' >");
         outputfileLines.add("<text>");
         outputfileLines.add("<![CDATA[");

         // search for the option text
         nodeList2 = simpleChoice.getChildNodes();
         String feedback = "";
         for (int y=0; y < nodeList2.getLength(); y++) {
            aNode = (Node)nodeList2.item(y);
            if (aNode.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
               answer = aNode.getNodeValue();
            }
            if (aNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
               String nameElem  = aNode.getNodeName();
               if (nameElem.equals("feedbackInodeListine")) {
                 feedback = aNode.getTextContent();
               }
            }
         }

         if (answer.equals("")) {
            System.out.println("WARNING: no text for this option");
            Translate.report.add(outputFileName+": Strange question: "+title+" (no text for this option)");
         }

         outputfileLines.add(answer);

         outputfileLines.add("]]>");
         outputfileLines.add("</text>");
         if (!feedback.equals("")) {
            outputfileLines.add("<feedback format=\"html\">");
            outputfileLines.add("<text><![CDATA[");
            outputfileLines.add(feedback);
            outputfileLines.add("]]></text>");
            outputfileLines.add("</feedback>");
         }
         outputfileLines.add("</answer>");
      }


      outputfileLines.add("</question>");
      outputfileLines.add("</quiz>");

      // store the translation
      Path path = Paths.get(outputFileName);
      BufferedWriter writer;

      try {
         writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);
       }
       catch (IOException ex) {
         System.out.println("ERROR: IOException opening output file: "+outputFileName);
         Translate.report.add("Could not open the output file to store the trasnlation of question '"+title+"'");
         return -1;
       }

      try {
        for (String line: outputfileLines) {
           writer.append(line);
           writer.newLine();
         }
       }
       catch (IOException ex) {
         System.out.println("ERROR: IOException writing output file: "+outputFileName);
         Translate.report.add("Could not save the file to store the trasnlation of question '"+title+"'");
         return -1;
       }

      try {
        writer.flush();
        writer.close();
      }
      catch (IOException ex) {
        System.out.println("ERROR: IOException closing output file: "+outputFileName+" --> "+ex.toString());
        Translate.report.add("Could not close the output file to store the trasnlation of question '"+title+"'");
        return -1;
      }

      return 0;
    }





    // search for the question statement
    // receives initially an <itemBody>, then recursively other elements
    public static Statement getStatement(Statement statement, String inputFolder, String question, Element e)
    {
      String nodeName;
      NodeList nodeList;
      Node aNode;

      // the format is
      // <object> with images
      // <![CDATA[ statement ]]>
      // <choiceInteraction>

      // let's see this node childs
      nodeList = e.getChildNodes();
      if (nodeList.getLength() == 0) {
         System.out.println("ERROR: an element <"+e.getNodeName()+"> with no childs, even #text");
         return statement;
      }

      // let's see this node childs
      for (int x=0; x < nodeList.getLength(); x++) {
         aNode = (Node)nodeList.item(x);

         switch (aNode.getNodeType()) {

            case org.w3c.dom.Node.TEXT_NODE:     // a little bit of text, add to the statement text
              statement.addTextStatement(aNode.getNodeValue().trim());
              break; // out of the switch, let's go with another for child

            case org.w3c.dom.Node.CDATA_SECTION_NODE:  // a CDATA, is the statement, add content to statement
              String cdataText = aNode.getNodeValue().trim();
              statement.addTextStatement(cdataText);
              if (cdataText.toLowerCase().contains("<img")) {
                  int posImg = cdataText.indexOf("<img");
                  int posSrc = cdataText.indexOf("src=", posImg);
                  int posQuot = cdataText.indexOf("\"", posSrc);
                  String url = cdataText.substring(1+posQuot);
                  if (url.startsWith("/")) {
                      System.out.println("WARNING: <img> inside the text of the question: '"+question+"'");
                      Translate.report.add("WARNING: there is an embedded image (not available), in the statement of question: '"+question+"'");
                  }
                }
              break; // out of the switch, let's go with another for child

            case org.w3c.dom.Node.ELEMENT_NODE:  // an element
              nodeName = aNode.getNodeName();
              if (!nodeName.equals("choiceInteraction")) {    // it is not a choiceInteraction, that is managed in main
                  if (nodeName.equals("object")) {  // an image
                     Element elElem = (Element)aNode;
                     String type = elElem.getAttribute("type");
                     if (type == null) {
                          Translate.report.add("WARNING: there is an attached file with unknown type in the statement of question "+question+", could not be incorporated");
                          break;   // this child does not add anything, get out of the switch, let's go with another for child
                     }

                     String fileName = elElem.getAttribute("data");  // file name
                     if (fileName == null) {
                          Translate.report.add("WARNING: there is an attached file with unknown name in the statement of question "+question+", could not be incorporated");
                          break;   // this child does not add anything, get out of the switch, let's go with another for child
                     }

                     if (type.contains("image/")) {
                         statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+fileName+"\" alt=\""+fileName+"\" class=\"img-responsive atto_image_button_text-bottom\"></center><br>");
                         String data64 = EncodeImage.encode64(inputFolder+fileName);
                         if (data64 == null) {   // problem encoding image to base64
                           Translate.report.add("WARNING: there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                           break;   // this child does not add anything, get out of the switch, let's go with another for child
                         }
                         statement.addImage(fileName, data64);
                     }
                     else
                         if (type.equals("application/pdf")) {
                             EncodeImage.Pdf2Image(inputFolder+fileName);
                             String data = fileName.replace(".pdf", ".png");
                             statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+data+"\"  width=\"700\" alt=\""+data+"\" class=\"img-responsive atto_image_button_text-bottom\"><center><br>");
                             String data64 = EncodeImage.encode64(inputFolder+data);
                             if (data64 == null) {  // problem encoding to base64 the PNG coming from the PDF
                               Translate.report.add("WARNING: there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                               break;  // this child does not add anything, get out of the switch, let's go with another for child
                             }
                             statement.addImage(data, data64);
                         }
                         else {
                             Translate.report.add("WARNING: there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                             break;   // this child does not add anything, get out of the switch, let's go with another for child
                         }
                  }
                  else {  // a distinct element, get inside recursively
                     statement.addTextStatement("<"+nodeName+">");
                     statement = TranslateMultipleChoice.getStatement(statement, inputFolder, question, (Element)aNode);
                     statement.addTextStatement("</"+nodeName+">");
                  }
              }
              break;  // out of the switch, let's go with another for child
         }  // close the swicth
      } // close for

      return statement;
    }




    public static ArrayList<Pair<String,String>> processGrades(ArrayList<Pair<String,String>> mappingsGrades, String cardinality) {

       // System.out.println("Notas originales");
       // for (Pair<String,String> p: mappingsGrades) {
          //System.out.println(p.getKey()+"="+p.getValue());
       //}

       // all of them can be 0

       if (cardinality.equals("true")) { // it is a multiple choice, unique answer
          // Claroline permits all them to have an unlimited absolute value   q
          // Claroline requests the correct option being positive (or 0) and the other ones negative (or 0)

          // Moodle request
          // if ther eus any positive , it must have one with 100% value, the correct one
          // the others are negative or 0, between -100% and 0%

          //so the adjust algorithm is
          // divide the positive grade (POS) by itself and multiply by 100, so the positive is 100%
          // if minimum negative has an absolute value lower than the absolute value of the positive, divide each negative by POS, so they lead to values between -100 and 0
          // if minimum negative (NEG) has an absolute value greater than the absolute value of the positive, divide each negative by NEG, so they lead to values between -100 and 0

          // search for the higher positive and the lower negative
          Float max = new Float(0);
          Float min = new Float(0);
          for (Pair<String,String> p: mappingsGrades) {
              String grade = p.getValue();
              Float f = new Float(grade);
              if (f > max) max = f;
              if (f < min) min = f;
          }


          // change N+ by N+/max *100, and negatives by N-/max *100 or N-/min *100
          for (int x=0; x<mappingsGrades.size(); x++) {
             Pair<String,String> p = mappingsGrades.get(x);
             String grade = p.getValue();
             Float f = new Float(grade);
             if (max == 0) {   // there are no positives greater than 0, 0 remains 0
                if (f < 0)
                    f = 100 * f / Math.abs(min);  // scale negatives proportional to the minimum for this being -100%
             }
             else {
                 if (f > 0) f = 100 * f / max;  // adjunt positive to 100%
                 if (f < 0) {
                   if (Math.abs(min) >= max)    // the absolute value of min is greater than max, scale to 100% for min
                        f = 100 * f / Math.abs(min);
                   else
                        f = 100 * f / max;  // scale negatives proportionaly to max
                 }
             }

             mappingsGrades.set(x, new Pair(p.getKey(), String.format(Locale.UK, "%.3f", f) ));
          }



       }
       else { // it is a multiple choice, multiple answer
           // Claroline permits all of them having an unlimited absolute value
           // Claroline requests correct ones being positive, and incorrect ones being negative

           // Moodle request than
           // the addition os correct ones to be 100%
           // the others are negative between -100% and 0%

           // so the adjust algorithm is
           // compute the addition of positives (SUM)
           // scale positives, dividing them by SUM, for their addition to be 100%
           // scale negatives dividing them by SUM
           // search minimum of negatives (MIN) in its current state
           // sif MIN is less than -100% scale negatives for MIN to be -100%

           Float summation = new Float(0);  // addition of positives

           // compute the addition of positives
           for (Pair<String,String> p: mappingsGrades) {
              String grade = p.getValue();
              Float f = new Float(grade);
              if (f > 0) summation += f;
           }

           // scale each grade to grade/addition * 100, transforming it to a percentage
           for (int x=0; x<mappingsGrades.size(); x++) {
              Pair<String,String> p = mappingsGrades.get(x);
              String grade = p.getValue();
              Float f = new Float(grade);
              if (summation == 0) f = 100 * f;  // if there is no positive, we only convert the negatives to percentages
              else f = 100 * f / summation;
              mappingsGrades.set(x, new Pair(p.getKey(), String.format(Locale.UK, "%.3f", f) ));
           }

           // search the min of negatives
           Float min = new Float(0);
           for (Pair<String,String> p: mappingsGrades) {
              String grade = p.getValue();
              Float f = new Float(grade);
              if (f < min) min = f;
           }

           // if there is a negative grade less than -100, scale the negatives for the min to be -100
           if (min < -100) {
              for (int x=0; x<mappingsGrades.size(); x++) {
                  Pair<String,String> p = mappingsGrades.get(x);
                  String grade = p.getValue();
                  Float f = new Float(grade);
                  if (f < 0) {
                      f = 100 * f / Math.abs(min);
                      mappingsGrades.set(x, new Pair(p.getKey(), String.format(Locale.UK, "%.3f", f) ));
                  }
              }
           }
       }

       //System.out.println("Notas ajustadas");
       //for (Pair<String,String> p: mappingsGrades) {
        //  System.out.println(p.getKey()+"="+p.getValue());
       //}

       return mappingsGrades;
    }



    public static String getValue(String v) {
      Float f = Float.parseFloat(v);
      List<Float> moodleList = new ArrayList<Float>(Arrays.asList(0f, 5f, 10f, 11.11111f, 12.5f, 14.28571f, 16.66667f, 20f, 25f, 30f, 33.33333f, 40f, 50f, 60f, 66.66667f, 70f, 75f, 80f, 83.33333f, 90f, 100f));
      List<Float> moodleListNeg = new ArrayList<Float>(Arrays.asList(-5f, -10f, -11.11111f, -12.5f, -14.28571f, -16.66667f, -20f, -25f, -30f, -33.33333f, -40f, -50f, -60f, -66.66667f, -70f, -75f, -80f, -83.33333f, -90f, -100f));

      moodleList.addAll(moodleListNeg);

      if (f > 100) {
        System.out.println("ERROR: grade greater than 100: "+f);
        System.exit(-1);
      }

      Float closest = TranslateMultipleChoice.closest(f, moodleList);
      //System.out.println("XXX "+v+" -->"+closest.toString());
      return closest.toString();
    }

    public static Float closest(Float of, List<Float> in) {
           Float min = Float.MAX_VALUE;
           Float closest = of;

           for (Float v : in) {
               Float diff = Math.abs(v - of);

               if (diff < min) {
                   min = diff;
                   closest = v;
               }
           }

           return closest;
    }


    // check if a statement is empty text
    public static boolean isEmpty(String statement) {
       String text;

       text = statement.replaceAll("<p>", "");
       text = text.replaceAll("</p>", "");
       text = text.replaceAll("<p/>", "");

       text = text.replaceAll("<!--.*-->", "");
       text = text.trim();

			 System.out.println("TEXT STATEMENT = "+text);

       if (text.startsWith("<img"))
          return true;

			 if (text.isEmpty())
			    return true;

       return false;
    }





}
