// class to encode a file content
// encode64: the file input (an image) is encoded to base64 and returedn it as String
// Pdf2Image: the file input (a PDF) is tranformed to PNG and stored in a new file

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;


public class EncodeImage {

    // encode to base64 a file with an image  and return it as a String
    public static String encode64 (String fileName)
    {
        InputStream inputStream;
        String encodedFile = "";
        Base64 base64 = new Base64();

        File fd = new File(fileName);
        byte[] fileArray = new byte[(int) fd.length()];

        try {
      			inputStream = new FileInputStream(fd);
      			inputStream.read(fileArray);
      			encodedFile = base64.encodeToString(fileArray);
        }
        catch (Exception ex) {
            System.out.println("ERROR: Problem encoding to base64. Exception: "+ex.toString());
            return null;
        }

        return encodedFile;
    }


    // transform a PDF file to PNG and store it
    public static int Pdf2Image (String fileNameIN)
    {
        File fdIN, fdOUT;
        BufferedImage image;
        String fileNameOUT = fileNameIN.replace(".pdf", ".png");
        String bi;

        try {
            fdIN = new File(fileNameIN);  // open the input file

            // create list of PDF pages
            PDDocument document = PDDocument.load(fileNameIN);
            List<PDPage> listPages = document.getDocumentCatalog().getAllPages();

            PDPage firstPage = listPages.get(0);  // process only first page
            image = firstPage.convertToImage();   // convert first page to image

            fdOUT = new File(fileNameOUT);
            ImageIO.write(image, "png", fdOUT);   // save image as PNG

            // for (PDPage page : listPages) {
            //     image = page.convertToImage();
            //     fdOUT = new File(destinationDir + fileNameSinExt + ".png");
            //     ImageIO.write(image, "png", fdOUT);
            //     pageNumber++;
            // }

            document.close();

        }
        catch (Exception ex) {
            System.out.println("ERROR: Problem encoding to PNG. Exception: "+ex.toString());
            return -1;
        }

        return 0;
    }



}
