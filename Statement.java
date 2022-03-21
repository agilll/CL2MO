// class to store a question statement,  together with images

import java.util.ArrayList;
import javafx.util.Pair;

class Statement {
    String textStatement;  // statement text
    ArrayList<String> imgs; // <img> elements after the statement
    String textOptions;   // statement options after the images
    ArrayList<Pair<String,String>> images;   // image data for every <img>

    public Statement () {
      textStatement = "";
      imgs = new ArrayList<String>();
      textOptions = "";
      images = new ArrayList<Pair<String,String>>();
    }

    // to add more text to the statement
    public void addTextStatement(String t) {
       this.textStatement = this.textStatement+t;
    }
    // to request the statement text
    public String getTextStatement() {
      return textStatement;
    }



    // to add a new <img>
    public void addImg(String eimg) {
       this.imgs.add(eimg);
    }
    // to request all the <img> elements
    public ArrayList<String> getImgs() {
      return imgs;
    }

    // to add a new image data
    public void addImage(String nombre, String data) {
       this.images.add(new Pair(nombre, data));
    }
    // to request all images data
    public ArrayList<Pair<String,String>>  getImages() {
      return images;
    }


    // to add more text to the options
    public void addTextOptions(String t) {
       this.textOptions = this.textOptions+t;
    }
    // to request the options text
    public String getTextOptions() {
      return textOptions;
    }




}
