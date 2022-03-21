// class to translate an InlineChoice question, to fill holes chossing words from a list

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

public class TranslateInlineChoice {

  // translate an InlineChoice question and saves it in the ouput filename
  // return -1 if translation could not be done
   public static int doInlineChoice(String outputFileName, String inputFolder, String encoding, String title, Element assessmentItem)
   {
      ArrayList<String> outputfileLines = new ArrayList<String>();

      Node aNode;
      NodeList nodeList, nodeList2;
      Element responseDeclaration, correctResponse, valueEl, mapping, mapEntry, itemBody;
      String identifier, idCorrectAnswer, textCorrectAnswer;

      // an array with the info of each hole
      ArrayList<InlineChoiceMapping> mappingsHoles = new ArrayList<InlineChoiceMapping>();

      // may exist several responseDeclaration, one for each hole
      nodeList = assessmentItem.getElementsByTagName("responseDeclaration");
      if (nodeList.getLength() == 0 ) {
         System.out.println("ERROR: No element <responseDeclaration>");
         Translate.report.add("Wrong question: "+title+" (no element <responseDeclaration>");
         return -1;
      }

      // study each hole
      for (int x=0; x < nodeList.getLength(); x++) {

          responseDeclaration = (Element)nodeList.item(x);
          identifier = responseDeclaration.getAttribute("identifier");
          if (identifier == null) {
             System.out.println("ERROR: <responseDeclaration> with no attribute 'identifier'");
             Translate.report.add("Wrong question: "+title+" (<responseDeclaration> with no attribute 'identifier')");
             return -1;
          }

          nodeList2 = responseDeclaration.getElementsByTagName("correctResponse");
          if (nodeList2.getLength() == 0 ) {
             System.out.println("ERROR: <responseDeclaration> with no child <correctResponse>");
             Translate.report.add("Wrong question: "+title+" (<responseDeclaration>  with no child  <correctResponse>)");
             return -1;
          }
          correctResponse = (Element)nodeList2.item(0);

          nodeList2 = correctResponse.getElementsByTagName("value");
          if (nodeList2.getLength() == 0 ) {
             System.out.println("ERROR: <correctResponse> with no child <value>");
             Translate.report.add("Wrong question: "+title+" (<correctResponse> with no child <value>)");
             return -1;
          }
          valueEl = (Element)nodeList2.item(0);
          idCorrectAnswer = valueEl.getTextContent().trim();   // correct answer identifier for this hole

          // get the correct answer text
          nodeList2 = responseDeclaration.getElementsByTagName("mapping");
          if (nodeList2.getLength() == 0 ) {
             System.out.println("ERROR: <responseDeclaration>  with no child <mapping>");
             Translate.report.add("Wrong question: "+title+" (<responseDeclaration> with no child <mapping>)");
             return -1;
          }
          mapping = (Element)nodeList2.item(0);

          nodeList2 = mapping.getElementsByTagName("mapEntry");
          if (nodeList2.getLength() == 0 ) {
             System.out.println("ERROR: <mapping> with no child <mapEntry>");
             Translate.report.add("Wrong question: "+title+" (<mapping> with no child <mapEntry>)");
             return -1;
          }
          mapEntry = (Element)nodeList2.item(0);

          // this is not used
          textCorrectAnswer = mapEntry.getAttribute("mapKey");
          if (textCorrectAnswer == null) {
             System.out.println("ERROR: <mapEntry> with no attribute 'mapKey'");
             Translate.report.add("Wrong question: "+title+" (<mapEntry> with no attribute 'mapKey')");
             return -1;
          }

          // add a new entry for this hole in the mappings array
          mappingsHoles.add(new InlineChoiceMapping(identifier, idCorrectAnswer, textCorrectAnswer));
      }


      // let's go with the question body, with the option for each hole

      nodeList = assessmentItem.getElementsByTagName("itemBody");
      if (nodeList.getLength() == 0 ) {
         System.out.println("ERROR: no <itemBody> found");
         Translate.report.add("Wrong question: "+title+" (no <itemBody> found)");
         return -1;
      }
      itemBody = (Element)nodeList.item(0);

      // serach the statement text and complete the mappings
     Statement statement = new Statement();
     statement = TranslateInlineChoice.getStatement(statement, inputFolder, title, itemBody, 0, mappingsHoles);

     // the statement is wrong
     if (statement == null) return -1;


     // all data read, let's write the question
     outputfileLines.add("<?xml version='1.0' encoding='"+encoding+"'?>");
     outputfileLines.add("<quiz>");
     outputfileLines.add("<question type='gapselect'>");
     outputfileLines.add("<name><text>"+title+"</text></name>");
     // outputfileLines.add("<!-- "+outputFileName+" -->");
     outputfileLines.add("<questiontext format='html'>");
     outputfileLines.add("<text><![CDATA[");

     outputfileLines.add(statement.getTextStatement());  // add the statement

     // add the <img> tags
     ArrayList<String> imgs = statement.getImgs();
     for (String eimg: imgs) {
        outputfileLines.add(eimg);
     }

     outputfileLines.add("<p>&nbsp;</p>");
     // add the options
     outputfileLines.add(statement.getTextOptions());

     outputfileLines.add("]]></text>");

     // get the images data for the <img> tags
     ArrayList<Pair<String,String>> images = statement.getImages();

     for (Pair<String,String> p: images) {
        outputfileLines.add("<file name=\""+p.getKey()+"\" path=\"/\" encoding=\"base64\">"+p.getValue()+"</file>");
     }
     outputfileLines.add("</questiontext>");

     outputfileLines.add("<defaultgrade>1</defaultgrade>");
     outputfileLines.add("<shuffleanswers>true</shuffleanswers>");  // always shuffle the answers
     outputfileLines.add("<shownumcorrect/>");


     // now add the set of holes with the position of the correct answer

     for (int x=0; x < mappingsHoles.size(); x++ ) {
       InlineChoiceMapping hole = mappingsHoles.get(x);  // get a hole

       ArrayList<Pair<String,String>>  options = hole.getOptions();  // get the different options for this hole

       // add the options for this hole
       // each option is composed of its text and the group it belongs to
       for (int y=0; y < options.size(); y++ ) {

          // pair (text, group)
          Pair<String,String> pair = options.get(y);

          outputfileLines.add("<selectoption>");
          outputfileLines.add("<text><![CDATA["+pair.getValue()+"]]></text>");
          outputfileLines.add("<group>"+(x+1)+"</group>");
          outputfileLines.add("</selectoption>");
       }
     }

     outputfileLines.add("</question>");
     outputfileLines.add("</quiz>");

     // all the translated question lines have been added

     // save the result
     Path path = Paths.get(outputFileName);
     BufferedWriter writer;

     try {
        writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1);

       for (String line: outputfileLines)
          writer.append(line+"\n");

       writer.close();
     }
     catch (IOException ex) {
       System.out.println("ERROR: IOException writing output file "+outputFileName);
       Translate.report.add("Could not be daved the output file'"+title+"'");
       return -1;
     }

     return 0;
   }



    // search the question text
    // and complete the info about the holes with their options
    // initially receives an <itemBody>, the recursively other descendant elements
    public static Statement getStatement(Statement statement, String inputFolder, String question, Element e, int numStoredOptions, ArrayList<InlineChoiceMapping> mappingsHoles)
    {
      String nodeName, responseIdentifier, identifier, cdata="";
      NodeList nodeList, nodeList2, nodeList3;
      Node aNode, aNode2;
      InlineChoiceMapping hole=null;
      Element inlineChoice, inlineChoiceInteraction;

      // the format is
      // <object> with the images
      // <prompt> the statement </prompt>
      // the options text with <inlineChoiceInteraction> elements in between

      // let's see this node childs
      nodeList = e.getChildNodes();
      if (nodeList.getLength() == 0) {
         System.out.println("ERROR: an element <"+e.getNodeName()+"> with no childs, even #text");
         return statement;
      }

      // let's study the childs of this element
      for (int x=0; x < nodeList.getLength(); x++) {
         aNode = (Node)nodeList.item(x);

         switch (aNode.getNodeType()) {

            case org.w3c.dom.Node.TEXT_NODE:      // a little bit of text, add to the options text
              statement.addTextOptions(aNode.getNodeValue().trim());
              break;  // out of the switch, let's go with another for child

            case org.w3c.dom.Node.CDATA_SECTION_NODE:  // it is a CDATA, add its content to the statement text
              if (aNode.getNodeValue().toLowerCase().contains("<img")) {
                  System.out.println("WARNING: <img> inside the text of the question: '"+question+"'");
                  Translate.report.add("WARNING: there is an embedded image (not available), in the statement of question: '"+question+"'");
                }
              statement.addTextStatement(aNode.getNodeValue().trim());
              break; // out of the switch, let's go with another for child

            case org.w3c.dom.Node.ELEMENT_NODE:  // it is an element
              nodeName = aNode.getNodeName();
              if (!nodeName.equals("inlineChoiceInteraction")) {    // it is not an <inlineChoiceInteraction>
                  if (nodeName.equals("object")) {
                     Element elemObject = (Element)aNode;
                     String type = elemObject.getAttribute("type");
                     if (type == null) {
                          Translate.report.add("WARNING: there is an attached file with unknown type in the statement of question "+question+", could not be incorporated");
                          break;   // this child does not add anything, get out of the switch, let's go with another for child
                     }

                     String fileName = elemObject.getAttribute("data");  // file name
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
                             if (data64 == null) {  // problem encoding to base 64 the PNG image translated from the PDF file
                               Translate.report.add("WARNING:  there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                               break;  // this child does not add anything, get out of the switch, let's go with another for child
                             }
                             statement.addImage(data, data64);
                         }
                         else {
                             Translate.report.add("WARNING:  there is an attached file named "+fileName+" with type "+type+" in the statement of question "+question+" that could not be incorporated");
                             break;   // this child does not add anything, get out of the switch, let's go with another for child
                         }
                  }
                  else {
                     if (nodeName.equals("prompt")) {  // this is the real text of the statement
                        Element elemPrompt = (Element)aNode;
                        statement.addTextStatement(elemPrompt.getTextContent().trim());
                        break;  // out of the switch, let's with another for child
                     }
                     else {
                        statement.addTextOptions("<"+nodeName+">");
                        statement = TranslateInlineChoice.getStatement(statement, inputFolder, question, (Element)aNode, numStoredOptions, mappingsHoles);
                        statement.addTextOptions("</"+nodeName+">");
                        break;  // out of the switch, let's with another for child
                     }
                  }
              }
              else {     // it is an <inlineChoiceInteraction>, we must study its hole and its options
                  inlineChoiceInteraction = (Element)aNode;
                  responseIdentifier = inlineChoiceInteraction.getAttribute("responseIdentifier");
                  if (responseIdentifier == null) {
                     System.out.println("ERROR: there is no hole identifier (responseIdentifier) in the <inlineChoiceInteraction>");
                     Translate.report.add("Strange question:  "+question+" (there is no hole identifier (responseIdentifier) in the <inlineChoiceInteraction>)");
                     break;  // this child does not add anything, get out of the switch, let's go with another for child
                  }

                  // search the object representing thos hole
                  for (int y=0; y < mappingsHoles.size(); y++) {
                      hole = mappingsHoles.get(y);
                      if (hole.getIdentifier().equals(responseIdentifier))
                          break;  // hole found, get out of this for
                  }

                  // hole is the object representing this hole <inlineChoiceInteraction>

                  int numNewOptions=0;  // number of new options added for this hole

                  nodeList2 = inlineChoiceInteraction.getElementsByTagName("inlineChoice");
                  if (nodeList2.getLength() == 0 ) {
                     System.out.println("WARNING: no options for a hole");
                     Translate.report.add("Strange question: "+question+" (no options for a hole)");
                     break; // this child does not add anything, get out of the switch, let's go with another for child
                  }

                  // go for the options of this hole
                  for (int y=0; y < nodeList2.getLength(); y++) {
                      inlineChoice = (Element)nodeList2.item(y);
                      identifier = inlineChoice.getAttribute("identifier");
                      if (identifier == null) {
                         System.out.println("WARNING: an option without attribute 'identifier'");
                         Translate.report.add("Strange question: "+question+" (an option without attribute 'identifier')");
                         continue;  // continue with anoother option
                      }

                      // search the CDATA with the text of the option
                      nodeList3 = inlineChoice.getChildNodes();
                      if (nodeList3.getLength() == 0 ) {
                         System.out.println("WARNING: an option without CDATA with the text");
                         Translate.report.add("Strange question: "+question+" (an option without CDATA with the text)");
                         continue; // continue with anoother option
                      }
                      // search  and read the text of the option
                      for (int z=0; z < nodeList3.getLength(); z++) {
                         aNode2 = nodeList3.item(z);
                         if (aNode2.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                             cdata = aNode2.getNodeValue();
                             break;  // text found
                         }
                      }

                      // add an option to the list of options for this hole
                      hole.addOption(identifier, cdata);
                      numNewOptions++;
                   }

                   int posCorrectOptionInWholeList = numStoredOptions + hole.getPosCorrectOption();

                   statement.addTextOptions(" [["+posCorrectOptionInWholeList+"]] ");
                   numStoredOptions = numStoredOptions + numNewOptions;
              }
              break; // continue with anoother for child
         }  // close the switch
      } // close the for

      return statement;
    }
}


// class to group all the information about a hole

class InlineChoiceMapping {
    String identifier;  // hole id
    String idCorrectAnswer;   // correct answer id
    String textCorrectAnswer; // correct answer text
    ArrayList<Pair<String,String>> options;   // hole options, each one is a pair (id, text)

    public InlineChoiceMapping (String i, String c, String m) {
      identifier = i;
      idCorrectAnswer = c;
      textCorrectAnswer = m;

      this.options = new ArrayList<Pair<String,String>>();
    }

    // to add an option for this hole
    public void addOption(String id, String text) {
       this.options.add(new Pair(id, text));
    }

    public String getIdentifier() {
      return identifier;
    }

    public ArrayList<Pair<String,String>>  getOptions() {
      return options;
    }

    // to request the position of the correct answer in this hole
    public int getPosCorrectOption() {
      for (int x=0; x < options.size(); x++) {
        Pair p = options.get(x);
        if (p.getKey().equals(idCorrectAnswer))
          return (x+1);
      }

      return -1;
    }



    // print the mapping, for debugging
    public void printMapping() {
      System.out.println("## "+identifier+" "+idCorrectAnswer+" "+textCorrectAnswer);

      for (int x=0; x < options.size(); x++) {
          Pair p = options.get(x);
          System.out.println("- "+p.getKey()+" "+p.getValue());
      }
    }

}
