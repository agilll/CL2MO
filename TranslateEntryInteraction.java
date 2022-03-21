// class to translate an InlineChoice question, fill holes writing

import java.util.ArrayList;
import javafx.util.Pair;
import java.io.IOException;

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


public class TranslateEntryInteraction {

   public static int doEntryInteraction(String outputFileName, String inputFolder, String encoding, String title, Element assessmentItem)
   {
      ArrayList<String> outputfileLines = new ArrayList<String>();

      NodeList nodeList, nodeList2;
      Element responseDeclaration, itemBody, mapEntry;
      String identifier, mapKey, mappedValue;

      ArrayList<Pair<String, Pair<String, String>>> holes = new ArrayList<Pair<String, Pair<String, String>>>();

      // may exist several responseDeclaration, one for each hole to fill
      nodeList = assessmentItem.getElementsByTagName("responseDeclaration");
      if (nodeList.getLength() == 0 ) {
         System.out.println("ERROR: no element <responseDeclaration>");
         Translate.report.add("Wrong question: "+title+" (no element <responseDeclaration>)");
         return -1;
      }

      for (int x=0; x < nodeList.getLength(); x++ ) {
         responseDeclaration = (Element)nodeList.item(x);
         identifier = responseDeclaration.getAttribute("identifier");
         if (identifier == null) {
             System.out.println("ERROR: <responseDeclaration> with no attribute 'identifier'");
             Translate.report.add("Wrong question: "+title+" (<responseDeclaration> with no attribute 'identifier')");
             return -1;
         }

         nodeList2 = responseDeclaration.getElementsByTagName("mapEntry");
         if (nodeList2.getLength() == 0 ) {
             System.out.println("ERROR: <responseDeclaration> with no child <mapEntry>");
             Translate.report.add("Wrong question: "+title+" (<responseDeclaration> with no child <mapEntry>)");
             return -1;
         }

         mapEntry = (Element)nodeList2.item(0);

         mapKey = mapEntry.getAttribute("mapKey");
         if (mapKey == null) {
             System.out.println("ERROR: Un <mapEntry> with no attribute'mapKey'");
             Translate.report.add("Wrong question: "+title+" (<mapEntry> with no attribute 'mapKey')");
             return -1;
         }

         // Claroline only permits positive numbers
         mappedValue = mapEntry.getAttribute("mappedValue");
         if (mappedValue == null) {
             System.out.println("ERROR: <mapEntry> with no attribute 'mappedValue'");
             Translate.report.add("Wrong question: "+title+" (<mapEntry> nwith no attribute 'mapValue')");
             return -1;
        }

        holes.add(new Pair(identifier, new Pair(mapKey,mappedValue)));
      }


      // let's see the question body with the holes

      nodeList = assessmentItem.getElementsByTagName("itemBody");
      if (nodeList.getLength() == 0 ) {
         System.out.println("ERROR: no <itemBody>");
         Translate.report.add("Wrong question: "+title+" (no <itemBody>)");
         return -1;
      }
      itemBody = (Element)nodeList.item(0);


      // search the question statement and complete the holes mappings
     Statement statement = new Statement();
     statement = TranslateEntryInteraction.getStatement(statement, inputFolder, title, itemBody, holes);

     // wrong statement
     if (statement == null) return -1;

     // let's fill the question
     outputfileLines.add("<?xml version='1.0' encoding='"+encoding+"'?>");
     outputfileLines.add("<quiz>");
     outputfileLines.add("<question type='cloze'>");
     outputfileLines.add("<name><text>"+title+"</text></name>");
     //outputfileLines.add("<!-- "+outputFileName+" -->");
     outputfileLines.add("<questiontext format='html'>");

     outputfileLines.add("<text><![CDATA[");

     outputfileLines.add(statement.getTextStatement());

      // add the <img> links
     ArrayList<String> imgs = statement.getImgs();
     for (String eimg: imgs) {
        outputfileLines.add(eimg);
     }

     outputfileLines.add("<p>&nbsp;</p>");
     outputfileLines.add(statement.getTextOptions());

     outputfileLines.add("]]></text>");

      // add the <img> data
     ArrayList<Pair<String,String>> imagenes = statement.getImages();

     for (Pair<String,String> p: imagenes) {
        outputfileLines.add("<file name=\""+p.getKey()+"\" path=\"/\" encoding=\"base64\">"+p.getValue()+"</file>");
     }
     outputfileLines.add("</questiontext>");

     outputfileLines.add("<defaultgrade>1</defaultgrade>");
     outputfileLines.add("<shuffleanswers>true</shuffleanswers>");  // always shuffle the answers
     outputfileLines.add("<shownumcorrect/>");

     outputfileLines.add("</question>");
     outputfileLines.add("</quiz>");

     // store the question
     Path path = Paths.get(outputFileName);
     BufferedWriter writer;

     try {
        writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

       for (String line: outputfileLines)
          writer.append(line+"\n");

       writer.close();
     }
     catch (IOException ex) {
       System.out.println("ERROR: IOException writing the output file "+outputFileName);
       Translate.report.add("IOException writing the output file '"+title+"'");
       //return -1;
       System.exit(-1);
     }

     return 0;
   }



    // search the statement text
    // process the holes
    // receives initially an <itemBody>, then recursively another elements
    public static Statement getStatement(Statement statement, String inputFolder, String question, Element e, ArrayList<Pair<String, Pair<String, String>>> holes)
    {
      String nodeName, responseIdentifier, identifier, cdata="";
      NodeList nodeList;
      Node aNode;
      Element entryInteraction;
      Pair<String, Pair<String, String>> hole = null;;

      // the format is
      // <object> with the embedded images
      // <prompt> teh statement  </prompt>
      // the text with the holes and the options with elements <textEntryInteraction> in between

      // let's see the childs for this node
      nodeList = e.getChildNodes();
      if (nodeList.getLength() == 0) {
         System.out.println("ERROR: an element <"+e.getNodeName()+"> without childs, even #text");
         return statement;
      }

      // let's see the childs for this element
      for (int x=0; x < nodeList.getLength(); x++) {
         aNode = (Node)nodeList.item(x);

         switch (aNode.getNodeType()) {

            case org.w3c.dom.Node.TEXT_NODE:      // a little bit of text, add to options text
              statement.addTextOptions(aNode.getNodeValue().trim());
              break;  // out of the switch, let's go with another for child

            case org.w3c.dom.Node.CDATA_SECTION_NODE:  // it is a CDATA, add its contents to the statement text
              if (aNode.getNodeValue().toLowerCase().contains("<img")) {
                  System.out.println("WARNING: <img> embedded in the statement text");
                  Translate.report.add("WARNING: there is an embedded image (not available) in the statement of question '"+question+"'");
              }
              statement.addTextStatement(aNode.getNodeValue().trim());
              break;  // out of the switch, let's go with another for child

            case org.w3c.dom.Node.ELEMENT_NODE:  // an element
              nodeName = aNode.getNodeName();
              if (!nodeName.equals("textEntryInteraction")) {    // it is not an  <textEntryInteraction>
                  if (nodeName.equals("object")) {
                     Element elElem = (Element)aNode;
                     String type = elElem.getAttribute("type");
                     if (type == null) {
                          Translate.report.add("WARNING: there is an embedded unknown file (not available) in the statement of question "+question+", that could not be incorporated");
                          break;    // this child does not add anything, get out of the switch, let's go with another for child
                     }

                     String fileName = elElem.getAttribute("data");  // nombre del fichero
                     if (fileName == null) {
                          Translate.report.add("WARNING: there is an embedded file (not available) with unknown name in the statement of question "+question+", that could not be incorporated");
                          break;    // this child does not add anything, get out of the switch, let's go with another for child
                     }

                     if (type.contains("image/")) {
                         statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+fileName+"\" alt=\""+fileName+"\" class=\"img-responsive atto_image_button_text-bottom\"></center><br>");
                         String data64 = EncodeImage.encode64(inputFolder+fileName);
                         if (data64 == null) {   // problem encoding image to base64
                           Translate.report.add("WARNING: there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                           break;    // this child does not add anything, get out of the switch, let's go with another for child
                         }
                         statement.addImage(fileName, data64);
                     }
                     else
                         if (type.equals("application/pdf")) {
                             EncodeImage.Pdf2Image(inputFolder+fileName);
                             String data = fileName.replace(".pdf", ".png");
                             statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+data+"\"  width=\"700\" alt=\""+data+"\" class=\"img-responsive atto_image_button_text-bottom\"><center><br>");
                             String data64 = EncodeImage.encode64(inputFolder+data);
                             if (data64 == null) {  // problem encoding to base64 the PNG from a PDF
                               Translate.report.add("WARNING: there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                               break;   // this child does not add anything, get out of the switch, let's go with another for child
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
                     statement = TranslateEntryInteraction.getStatement(statement, inputFolder, question, (Element)aNode, holes);
                     statement.addTextStatement("</"+nodeName+">");
                  }
              }
              else {     // it is a <textEntryInteraction>, a new hole must be added
                  entryInteraction = (Element)aNode;
                  responseIdentifier = entryInteraction.getAttribute("responseIdentifier");
                  if (responseIdentifier == null) {
                     System.out.println("WARNING: there is no hole identifier (responseIdentifier) in <entryInteraction>");
                     Translate.report.add("Strange question: "+question+" (there is no hole identifier (responseIdentifier) in the <entryInteraction>)");
                     break;    // this child does not add anything, get out of the switch, let's go with another for child
                  }

                  // search the first value disctint from 0
                  Float min = new Float(0);
                  for (int y=0; y < holes.size(); y++) {
                      hole = holes.get(y);
                      Float f = new Float(hole.getValue().getValue());
                      if (f > 0) { // found, hhere is at least a value greater than 0
                        min=f;
                        break;  // found, get out of for
                      }
                  }

                  // serach the minimum weight, always positive (id 0, this hole does not contribute)
                  for (int y=0; y < holes.size(); y++) {
                      hole = holes.get(y);
                      Float f = new Float(hole.getValue().getValue());
                      if ((f > 0) && (f < min)) min=f;
                  }

                  // min is the minimum value of all the positives (0 if all of them are 0)
                  // search for the object representing this hole
                  for (int y=0; y < holes.size(); y++) {
                      hole = holes.get(y);
                      if (hole.getKey().equals(responseIdentifier))
                          break;  // found, get out of for
                  }

                  // hole id the object corresponding to this hole, Pair<String=identifier, Pair<String=answer, String=weight>>
                  String response = hole.getValue().getKey();
                  String value = hole.getValue().getValue();

                  Float valueF = new Float(value);
                  int valueD = 0;
                  if (valueF > 0) valueD = Math.round(valueF / min);

                  // Moodle requests value to be an Integer
                  // we take as reference the lowest, and we set the Integer closer to its relation
                  statement.addTextOptions(" {"+valueD+":SA:="+response+"} ");
              }
              break; // let's go with another for child
         } // close switch
      }  // close for

      return statement;
    } // close method
}
