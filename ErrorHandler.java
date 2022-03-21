// error handler

import java.util.ArrayList;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;


class ErrorHandler extends DefaultHandler  {

	ArrayList<String> warnings;
	ArrayList<String> errors;
	ArrayList<String> fatalerrors;

	public ErrorHandler ()  {
		warnings = new ArrayList<String>();
		errors = new ArrayList<String>();
		fatalerrors = new ArrayList<String>();
	}

	public void warning(SAXParseException spe)  {
    System.out.println("WARNING");
		warnings.add(spe.toString());
	}

	public void error(SAXParseException spe)  {
    System.out.println("ERROR: "+spe.toString());
		errors.add(spe.toString());
	}

	public void fatalerror(SAXParseException spe) {
    System.out.println("FATAL ERROR");
		fatalerrors.add(spe.toString());
	}


	public boolean hasWarnings() {
		if (warnings.size() > 0) return true;
		else return false;
	}

	public boolean hasErrors() {
		if (errors.size() > 0) return true;
		else return false;
	}

	public boolean hasFatalerrors() {
		if (fatalerrors.size() > 0) return true;
		else return false;
	}


	public ArrayList<String> getWarnings() {
		return warnings;
	}

	public ArrayList<String> getErrors() {
		return errors;
	}

	public ArrayList<String> getFatalerrors() {
		return fatalerrors;
	}


	public void clear () {
		warnings.clear();
		errors.clear();
		fatalerrors.clear();
	}
}
