package vtrainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.jdom.Element;

public class DictionaryEntry implements Comparable {

    private Integer id = null;
    private String name;
    private String notes = null;
    private List translations = new ArrayList();
    private long lastTested = 0l;

    private int difficulty = 0;

    public Integer getID() {
        return id;
    }

    public void setID(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public void addToDifficulty(int delta) {
        difficulty += delta;
        // never let difficulty become negative
        difficulty = Math.max(difficulty, 0);
    }

    public long getLastTested() {
        return lastTested;
    }

    public void setLastTested(long lastTested) {
        this.lastTested = lastTested;
    }

    public List getTranslations() {
        return translations;
    }

    public boolean isTranslation(String text) {

        if (text == null) {
            throw new IllegalArgumentException("isTranslation called with null text");
        }

        boolean found = false;

        for (Iterator it = translations.iterator(); it.hasNext();) {
            String t = (String)it.next();
            found = t.equalsIgnoreCase(text.trim());
            if (found)
                break;
        }

        return found;
    }

    public Element toXML() {
        Element xml = new Element("dictionary-entry");
        xml.setAttribute("id", "" + getID());
        xml.setAttribute("name", getName());

        if (getDifficulty() != 0) {
            xml.setAttribute("difficulty", "" + getDifficulty());
        }
        if (getLastTested() != 0) {
            xml.setAttribute("last-tested", "" + getLastTested());
        }

        for (Iterator it = translations.iterator(); it.hasNext();) {
            String t = (String)it.next();
            Element txml = new Element("translation");
            txml.setText(t);
            xml.addContent(txml);
        }

        if (notes != null) {
            Element nxml = new Element("notes");
            nxml.setText(getNotes());
            xml.addContent(nxml);
        }

        return xml;
    }

    public static DictionaryEntry createFromXML(Element e) {
        DictionaryEntry de = new DictionaryEntry();
        de.setID(new Integer(e.getAttributeValue("id")));
        de.setName(e.getAttributeValue("name"));

        if (e.getAttribute("difficulty") != null) {
            // backwards compatibility, enforce min difficulty of 0
            de.setDifficulty(Math.max(0, Integer.parseInt(e.getAttributeValue("difficulty"))));
        }

        if (e.getAttribute("last-tested") != null) {
            de.setLastTested(Integer.parseInt(e
                    .getAttributeValue("last-tested")));
        }

        for (Iterator it = e.getChildren("translation").iterator(); it.hasNext();) {
            Element te = (Element)it.next();
            if (!"".equals(te.getText())) {
                de.getTranslations().add(te.getText());
            }
        }

        if (e.getChild("notes") != null) {
            de.setNotes(e.getChildText("notes"));
        }

        return de;
    }

    public String toString() {
        return getName();
    }

    public String toLargeString() {

        String msg = getName() + "\n\n";

        for (Iterator it = translations.iterator(); it.hasNext();) {
            String t = (String)it.next();
            msg += " - " + t + "\n";
        }

        if (notes != null) {
            msg += "\n\nnotes:\n" + notes + "\n";
        }

        return msg;
    }

    public int compareTo(Object other) {
        return name.toLowerCase().compareTo(((DictionaryEntry)other).getName().toLowerCase());
    }

    public static class DifficultyWithRandomComparator implements Comparator {
        private static Random random = new Random(System.currentTimeMillis());
        public int compare(Object o1, Object o2) {
            Integer d1 = new Integer(((DictionaryEntry)o1).getDifficulty());
            Integer d2 = new Integer(((DictionaryEntry)o2).getDifficulty());
            int randomNoise = (int)(10.0 * random.nextDouble());
            return d1.compareTo(d2 + randomNoise);
        }
    }

}
