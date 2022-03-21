// class to translate a  MatchInteraction question (relation question)

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

public class TranslateMatchInteraction {

    public static int doMatchInteraction(String outputFileName, String inputFolder, String encoding, String title, Element assessmentItem)
    {
       ArrayList<String> outputfileLines = new ArrayList<String>();

       Node aNode;
       NodeList nodeList;
       Element responseDeclaration, correctResponse, value, itemBody, matchInteraction, mapEntry;
       String valueField, answer="", identifier;
       String[] arrayValues;

       ArrayList<String> lefts = new ArrayList<String>();  // to store the ids of the fields to classify
       ArrayList<String> rights = new ArrayList<String>();  // to store the ids of the fields to assign
       // in the same position is the id of a field and the id of its correct value

       // to store the pairs (identifier, cdata) of each simpleAssociableChoice, that is, an option identifier and its text
       // the identifier may correspond to a field or to a value, and the text is the one to be presented
       ArrayList<Pair<String,String>> mappingsOptions = new ArrayList<Pair<String,String>>();

       String idLeft, idRight;
       Pair pairLeft=null, pairRight=null;

       nodeList = assessmentItem.getElementsByTagName("responseDeclaration");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <responseDeclaration> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <responseDeclaration> elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       responseDeclaration = (Element)nodeList.item(0);

       nodeList = responseDeclaration.getElementsByTagName("correctResponse");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <correctResponse> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <correctResponse> elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       correctResponse = (Element)nodeList.item(0);

       nodeList = correctResponse.getElementsByTagName("value");
       if (nodeList.getLength() == 0) {
          System.out.println("ERROR: no <value> elements in <correctResponse>");
          Translate.report.add("Wrong question: "+title+" (no <value> elements in <correctResponse>)");
          return -1;
       }

       // we read all mappings id field to id correct value
       for (int x=0; x < nodeList.getLength(); x++) {
          value = (Element)nodeList.item(x);
          valueField = value.getTextContent().trim();   // content, must be "field value"
          arrayValues =  valueField.split(" ");  // split both parts

          // add this new pair (idField, idCorrectValue)
          lefts.add(arrayValues[0].trim());
          rights.add(arrayValues[1].trim());
       }


       // search for the question body
       nodeList = assessmentItem.getElementsByTagName("itemBody");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <itemBody> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <itemBody> elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       itemBody = (Element)nodeList.item(0);

      Statement statement = new Statement();

       // search for the <object> with images

       nodeList = itemBody.getElementsByTagName("object");
       for (int x=0; x < nodeList.getLength(); x++) {
           Element elemObject = (Element)nodeList.item(x);
           String type = elemObject.getAttribute("type");
           if (type == null) {
                Translate.report.add("WARNING: there is an embedded file (not available) with unknown type in the statement of question "+title+", that could not be incorporated");
                continue;  // continue with another object
           }

           String nameFile = elemObject.getAttribute("data");  // nombre del fichero
           if (nameFile == null) {
                Translate.report.add("WARNING: there is an embedded file (not available) with unknown name in the statement of question "+title+", that could not be incorporated");
                continue;  // continue with another object
           }

           if (type.contains("image/")) {
               String data64 = EncodeImage.encode64(inputFolder+nameFile);
               if (data64 == null) {   // problem encoding image to base64
                 Translate.report.add("WARNING: there is an attached file named "+nameFile+" with type "+type+" in the statement of question "+title+" that could not be incorporated");
                 continue;  // continue with another object
               }
               statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+nameFile+"\" alt=\""+nameFile+"\" class=\"img-responsive atto_image_button_text-bottom\"></center><br>");
               statement.addImage(nameFile, data64);
           }
           else
               if (type.equals("application/pdf")) {
                   EncodeImage.Pdf2Image(inputFolder+nameFile);
                   String data = nameFile.replace(".pdf", ".png");
                   String data64 = EncodeImage.encode64(inputFolder+data);
                   if (data64 == null) {  // problem encoding to base64 the PNG from the PDF
                     Translate.report.add("WARNING: there is an attached file named "+nameFile+" with type "+type+" in the statement of question "+title+" that could not be incorporated");
                     continue;  // continue with another object
                   }
                   statement.addImg("<center><img src=\"@@PLUGINFILE@@/"+data+"\"  width=\"700\" alt=\""+data+"\" class=\"img-responsive atto_image_button_text-bottom\"><center><br>");
                   statement.addImage(data, data64);
               }
               else {  // continue with another object
                   Translate.report.add("WARNING: there is an attached file named "+nameFile+" with type "+type+" in the statement of question "+title+" that could not be incorporated");
               }
       }

       // search for the <matchInteraction>, with the statement

       nodeList = itemBody.getElementsByTagName("matchInteraction");
       if (nodeList.getLength() != 1) {
          System.out.println("ERROR: there are a number of <matchInteraction> elements different from 1. There are "+nodeList.getLength());
          Translate.report.add("Wrong question: "+title+" (there are a number of <matchInteraction>  elements different from 1. There are "+nodeList.getLength()+")");
          return -1;
       }
       matchInteraction = (Element)nodeList.item(0);

       // search for the question statement in the <matchInteraction>, and complete the mappings

      statement = TranslateMatchInteraction.getStatement(statement, title, matchInteraction, mappingsOptions);

      // create the translation
      outputfileLines.add("<?xml version='1.0' encoding='"+encoding+"'?>");
      outputfileLines.add("<quiz>");
      outputfileLines.add("<question type='matching'>");    // type matching
      outputfileLines.add("<name><text>"+title+"</text></name>");
      // outputfileLines.add("<!-- "+outputFileName+" -->");
      outputfileLines.add("<questiontext format='html'>");
      outputfileLines.add("<text>");

      outputfileLines.add("<![CDATA[");
      outputfileLines.add(statement.getTextStatement());

      ArrayList<String> imgs = statement.getImgs();
      for (String eimg: imgs) {
         outputfileLines.add(eimg);
      }

      outputfileLines.add("]]>");

      outputfileLines.add("</text>");

      // add the images
      ArrayList<Pair<String,String>> images = statement.getImages();

      for (Pair<String,String> p: images) {
         outputfileLines.add("<file name=\""+p.getKey()+"\" path=\"/\" encoding=\"base64\">"+p.getValue()+"</file>");
      }

      outputfileLines.add("</questiontext>");

      outputfileLines.add("<defaultgrade>1</defaultgrade>");
      outputfileLines.add("<shuffleanswers>true</shuffleanswers>");   // allways shuffle the answers

      // let's go with pairing lines, they are the fields with their values, and later the values without fields
      ArrayList<String> valuesAlreadyWithField = new ArrayList<String>();
      for (int x=0; x < lefts.size(); x++) {

          idLeft = lefts.get(x);     // the id of the field to classify
          idRight = rights.get(x);   // the id of its correct value

          // search for the filed id and its text
          for (int y=0; y < mappingsOptions.size(); y++) {
            pairLeft = mappingsOptions.get(y);
            if (pairLeft.getKey().equals(idLeft))
              break;
          }

          // search for the pair with the id of its correct value and its text
          for (int y=0; y < mappingsOptions.size(); y++) {
            pairRight = mappingsOptions.get(y);
            if (pairRight.getKey().equals(idRight))
              break;
          }

          // add the line of a field to classify
          outputfileLines.add("<subquestion format='html'>");
          outputfileLines.add("<text><![CDATA[");
          outputfileLines.add((String)pairLeft.getValue());
          outputfileLines.add("]]></text>");
          outputfileLines.add("<answer>");
          outputfileLines.add("<text><![CDATA[");
          outputfileLines.add((String)pairRight.getValue());
          outputfileLines.add("]]></text>");
          outputfileLines.add("</answer>");
          outputfileLines.add("</subquestion>");

          valuesAlreadyWithField.add(idRight);
     }

     // now add the values without field
     for (Pair<String,String> pair: mappingsOptions) {
          String ident = pair.getKey();
          if (lefts.contains(ident)) continue;   // if a field, we skip it
          if (valuesAlreadyWithField.contains(ident)) continue;  // if an already used value, we skip it s
          valuesAlreadyWithField.add(ident);  // a new value, add to the already used list

          // add a block for this value without field
          outputfileLines.add("<subquestion format='html'>");
          outputfileLines.add("<text></text>");
          outputfileLines.add("<answer>");
          outputfileLines.add("<text><![CDATA[");
          outputfileLines.add(pair.getValue());
          outputfileLines.add("]]></text>");
          outputfileLines.add("</answer>");
          outputfileLines.add("</subquestion>");
     }

     outputfileLines.add("</question>");
     outputfileLines.add("</quiz>");


     Path path = Paths.get(outputFileName);
     BufferedWriter writer;

     try {
        writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

       for (String line: outputfileLines)
          writer.append(line+"\n");

       writer.close();
     }
     catch (IOException ex) {
       System.out.println("ERROR: IOException creating output file "+outputFileName+" --> "+ex.toString());
       Translate.report.add("Could not create output file to save translation of question '"+title+"'");
       return -1;
     }


     return 0;
  }



    // search for the question statement
    // receives initially an element <matchInteraction>

    public static Statement getStatement(Statement statement, String pregunta, Element elem, ArrayList<Pair<String,String>> mappingsOptions)
    {
      String result="", nodeName, identifier, cdata="";
      NodeList nodeList, nodeList2, nodeList3;
      Node aNode, aNode2;
      Element simpleMatchSet, simpleAssociableChoice;

      // the format is
      // <prompt> the statement  </prompt>
      // elements <simpleMatchSet>

      // the <object> with the images go befiore this element and are managed in main

      nodeList = elem.getChildNodes();
      if (nodeList.getLength() == 0) {
         System.out.println("ERROR: an element <"+elem.getNodeName()+"> with no childs, even #text");
         return statement;
      }

      // let's see the childs of this element
      for (int x=0; x < nodeList.getLength(); x++) {
         aNode = (Node)nodeList.item(x);

         switch (aNode.getNodeType()) {

            case org.w3c.dom.Node.TEXT_NODE:    // a little bit of text, add to the statement
              statement.addTextStatement(aNode.getNodeValue().trim());
              break;

            case org.w3c.dom.Node.CDATA_SECTION_NODE:  // a CDATA, should not happen, add to the statement
              String cdataText = aNode.getNodeValue().trim();
              statement.addTextStatement(cdataText);
              if (cdataText.toLowerCase().contains("<img")) {
                  int posImg = cdataText.indexOf("<img");
                  int posSrc = cdataText.indexOf("src=", posImg);
                  int posQuot = cdataText.indexOf("\"", posSrc);
                  String url = cdataText.substring(1+posQuot);
                  if (url.startsWith("/")) {
                      System.out.println("WARNING: <img> embedded in the statement text of the question: '"+pregunta+"'");
                      Translate.report.add("WARNING: there is an embedded image (not available) in the statement of question: '"+pregunta+"'");
                  }
                }
              break;

            case org.w3c.dom.Node.ELEMENT_NODE:  // an element
              nodeName = aNode.getNodeName();
              if (!nodeName.equals("simpleMatchSet")) {    // an unknown element, add it and search recursively inside
                  if (nodeName.equals("prompt")) {
                      Element elemPrompt = (Element)aNode;
                      statement.addTextStatement(elemPrompt.getTextContent().trim());
                  }
                  else {  // should not happen
                      statement.addTextStatement("<"+nodeName+">");
                      statement = TranslateMatchInteraction.getStatement(statement, pregunta, (Element)aNode, mappingsOptions);
                      statement.addTextStatement("</"+nodeName+">");
                  }
              }
              else {   // a simpleMatchSet, study the options
                   simpleMatchSet = (Element)aNode;
                   nodeList2 = simpleMatchSet.getElementsByTagName("simpleAssociableChoice");

                   if (nodeList2.getLength() == 0) {
                      System.out.println("WARNING: no elements <simpleAssociableChoice> in <simpleMatchSet>");
                      Translate.report.add("Strange question: "+pregunta+" (no elements  <simpleAssociableChoice> in <simpleMatchSet>)");
                      continue;
                   }

                   // let's see the options, simpleAssociableChoice, that may be fields or values
                   for (int y=0; y < nodeList2.getLength(); y++) {
                       simpleAssociableChoice = (Element)nodeList2.item(y);

                       identifier = simpleAssociableChoice.getAttribute("identifier");  // an option identifier
                       if (identifier == null) {
                          System.out.println("WARNING: an element <simpleAssociableChoice> with no attribute 'identifier'");
                          Translate.report.add("Strange question: "+pregunta+" (an element <simpleAssociableChoice> with no attribute 'identifier')");
                          continue;
                       }

                       // search for the text of this option
                       nodeList3 = simpleAssociableChoice.getChildNodes();
                       if (nodeList3.getLength() == 0) {
                          System.out.println("WARNING: the option of a <simpleAssociableChoice> element has no CDATA with the option text");
                          Translate.report.add("Strange question: "+pregunta+" (the option of a <simpleAssociableChoice> element has no CDATA with the option text)");
                          continue;
                       }
                       for (int z=0; z < nodeList3.getLength(); z++) {
                          aNode2 = (Node)nodeList3.item(z);
                          if (aNode2.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {  // found CDATA with the text
                             cdata = aNode2.getNodeValue();
                             break;
                          }
                       }

                       mappingsOptions.add(new Pair(identifier, cdata));
                   }
              }
              break;
         }
      }

      return statement;
    }
}
