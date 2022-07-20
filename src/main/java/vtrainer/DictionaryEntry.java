package vtrainer;

import org.jdom.Element;

import java.util.*;

public class DictionaryEntry implements Comparable<DictionaryEntry> {

    private static final String DIFFICULTY_ATTR = "difficulty";
    private static final String LAST_TESTED_ATTR = "last-tested";
    private static final String CREATED_ATTR = "created";
    private static final String TRANSLATION_ELEM = "translation";
    private static final String TAG_ELEM = "tag";
    private static final String NOTES_ELEM = "notes";
    private static final String NAME_ATTR = "name";
    private static final String ID_ATTR = "id";
    private static final String DICTIONARY_ENTRY_ELEM = "dictionary-entry";

    private static final Random random = new Random(System.currentTimeMillis());

    private Integer id = null;
    private String name;
    private String notes = null;
    private List<String> translations = new ArrayList<>();

    private List<String> tags = new ArrayList<>();

    private long lastTested = 0l;
    private long created = 0l;

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
        //difficulty = Math.max(difficulty, 0);
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getLastTested() {
        return lastTested;
    }

    public void setLastTested(long lastTested) {
        this.lastTested = lastTested;
    }

    public List<String> getTranslations() {
        return translations;
    }

    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            tags.sort(String::compareToIgnoreCase);
        }
    }

    public List<String> getTags() {
        return tags;
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    public String getRandomTranslation() {
        return translations.get(random.nextInt(translations.size()));
    }

    public boolean isTranslation(String text) {

        if (text == null) {
            throw new IllegalArgumentException("isTranslation called with null text");
        }

        boolean found = false;

        for (Iterator<String> it = translations.iterator(); it.hasNext(); ) {
            String t = it.next();
            found = t.equalsIgnoreCase(text.trim());
            if (found)
                break;
        }

        return found;
    }

    public Element toXML() {
        Element xml = new Element(DICTIONARY_ENTRY_ELEM);
        xml.setAttribute(ID_ATTR, "" + getID());
        xml.setAttribute(NAME_ATTR, getName());

        if (getDifficulty() != 0) {
            xml.setAttribute(DIFFICULTY_ATTR, "" + getDifficulty());
        }
        if (getLastTested() != 0) {
            xml.setAttribute(LAST_TESTED_ATTR, "" + getLastTested());
        }
        if (getCreated() != 0) {
            xml.setAttribute(CREATED_ATTR, "" + getCreated());
        }

        for (Iterator<String> it = translations.iterator(); it.hasNext(); ) {
            String t = it.next();
            Element txml = new Element(TRANSLATION_ELEM);
            txml.setText(t);
            xml.addContent(txml);
        }

        for (Iterator<String> it = tags.iterator(); it.hasNext(); ) {
            String t = it.next();
            Element txml = new Element(TAG_ELEM);
            txml.setText(t);
            xml.addContent(txml);
        }

        if (notes != null) {
            Element nxml = new Element(NOTES_ELEM);
            nxml.setText(getNotes());
            xml.addContent(nxml);
        }

        return xml;
    }

    public static DictionaryEntry createFromXML(Element e) {
        DictionaryEntry de = new DictionaryEntry();
        de.setID(new Integer(e.getAttributeValue(ID_ATTR)));
        de.setName(e.getAttributeValue(NAME_ATTR));

        if (e.getAttribute(DIFFICULTY_ATTR) != null) {
            // backwards compatibility, enforce min difficulty of 0
            de.setDifficulty(Math.max(0, Integer.parseInt(e.getAttributeValue(DIFFICULTY_ATTR))));
        }

        if (e.getAttribute(LAST_TESTED_ATTR) != null) {
            de.setLastTested(Long.parseLong(e
                    .getAttributeValue(LAST_TESTED_ATTR)));
        }

        if (e.getAttribute(CREATED_ATTR) != null) {
            de.setCreated(Long.parseLong(e
                    .getAttributeValue(CREATED_ATTR)));
        }

        for (Iterator it = e.getChildren(TRANSLATION_ELEM).iterator(); it.hasNext(); ) {
            Element te = (Element) it.next();
            if (!"".equals(te.getText())) {
                de.getTranslations().add(te.getText());
            }
        }

        for (Iterator it = e.getChildren(TAG_ELEM).iterator(); it.hasNext(); ) {
            Element te = (Element) it.next();
            if (!"".equals(te.getText())) {
                de.addTag(te.getText());
            }
        }

        if (e.getChild(NOTES_ELEM) != null) {
            de.setNotes(e.getChildText(NOTES_ELEM));
        }

        return de;
    }

    public String toString() {
        return getName();
    }

    public String toSummaryString() {
        return getName() + " - " + String.join(", ", getTranslations());
    }

    public String toLargeString() {

        String msg = getName() + "\n\n";

        for (Iterator<String> it = translations.iterator(); it.hasNext(); ) {
            String t = it.next();
            msg += " - " + t + "\n";
        }

        if (notes != null) {
            msg += "\n\nnotes:\n" + notes + "\n";
        }

        return msg;
    }

    public int compareTo(DictionaryEntry other) {
        return name.toLowerCase().compareTo(other.getName().toLowerCase());
    }

    public static class DifficultyWithRandomComparator implements Comparator {
        private static Random random = new Random(System.currentTimeMillis());

        public int compare(Object o1, Object o2) {
            Integer d1 = new Integer(((DictionaryEntry) o1).getDifficulty());
            Integer d2 = new Integer(((DictionaryEntry) o2).getDifficulty());
            int randomNoise = (int) (10.0 * random.nextDouble());
            return d1.compareTo(d2 + randomNoise);
        }
    }

}
