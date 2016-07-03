package vtrainer;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jdom.Element;

import com.signal7.util.DataUtil;

public class Dictionary {

    private List entries = new ArrayList();
    private int highscore = 0;
    private Map reverseMap = new HashMap();
    private Map forwardMap = new HashMap();
    private Locale locale = Locale.getDefault();
    private Collator collator = Collator.getInstance(locale);

    public Dictionary() {
        collator.setStrength(Collator.PRIMARY);
    }

    public int getHighscore() {
        return highscore;
    }

    public void setHighscore(int highscore) {
        this.highscore = highscore;
    }

    public List getEntries() {
        return entries;
    }

    public void addEntry(DictionaryEntry d) {
        d.setID(getNextID());
        entries.add(d);
        Collections.sort(entries, new Comparator() {
            public int compare(Object o1, Object o2) {
                DictionaryEntry d1 = (DictionaryEntry)o1;
                DictionaryEntry d2 = (DictionaryEntry)o2;

                return collator.compare(d1.getName(), d2.getName());
            }
        });
        refreshMaps();
    }

    private Integer getNextID() {
        Integer maxID = new Integer(0);
        for (Iterator it = entries.iterator(); it.hasNext();) {
            DictionaryEntry d = (DictionaryEntry)it.next();
            Integer id = d.getID();
            if (id.intValue() > maxID.intValue()) {
                maxID = id;
            }
        }
        return new Integer(maxID.intValue() + 1);
    }

    public Element toXML() {

        Element xml = new Element("dictionary");

        xml.setAttribute("highscore", "" + highscore);
        xml.setAttribute("language", "" + locale.getLanguage());

        for (Iterator it = entries.iterator(); it.hasNext();) {
            DictionaryEntry de = (DictionaryEntry)it.next();
            xml.addContent(de.toXML());
        }

        return xml;
    }

    public static Dictionary createFromXML(Element e) {
        Dictionary d = new Dictionary();

        String highscoreVal = e.getAttributeValue("highscore");
        if (highscoreVal != null) {
            d.setHighscore(Integer.parseInt(highscoreVal));
        }

        String languageValue = e.getAttributeValue("language");
        if (languageValue != null) {
            d.setLocale(new Locale(languageValue));
            System.out.println("dictionary language " + d.getLocale().getDisplayLanguage());
        }

        for (Iterator it = e.getChildren("dictionary-entry").iterator(); it.hasNext();) {
            d.getEntries().add(DictionaryEntry.createFromXML((Element)it.next()));
        }
        return d;
    }

    private Locale getLocale() {
        return locale;
    }

    private void setLocale(Locale locale) {
        this.locale = locale;
        collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
    }

    public Map getReverseMap() {
        return reverseMap;
    }

    public Map getForwardMap() {
        return forwardMap;
    }

    public List createRandomList(int size) {
        if (size > entries.size()) {
            size = entries.size();
        }

        List copy = new ArrayList(entries);
        Collections.shuffle(copy);
        Collections.sort(copy, new DictionaryEntry.DifficultyWithRandomComparator());
        Collections.reverse(copy);

        return copy.subList(0, size);
    }

    public DictionaryEntry getMostDifficultEntry() {
        DictionaryEntry entry = (DictionaryEntry)DataUtil
                .getFirst(createRandomList(entries.size()));

        return entry;
    }

    public int getAverageDifficulty() {
        int sum = 0;

        int avg = 0;

        if (entries.size() > 0) {
            for (Iterator iter = entries.iterator(); iter.hasNext();) {
                DictionaryEntry entry = (DictionaryEntry)iter.next();
                sum += entry.getDifficulty();
            }
            avg = (int)((double)sum / (double)entries.size());
        }

        return avg;
    }

    public void refreshMaps() {
        forwardMap.clear();
        reverseMap.clear();
        for (Iterator it = entries.iterator(); it.hasNext();) {
            DictionaryEntry entry = (DictionaryEntry)it.next();
            String name = entry.getName();
            forwardMap.put(name, entry);
            for (Iterator itt = entry.getTranslations().iterator(); itt.hasNext();) {
                String translation = (String)itt.next();
                List reverseTranslations = (List)reverseMap.get(translation);
                if (reverseTranslations == null) {
                    reverseTranslations = new ArrayList();
                    reverseMap.put(translation, reverseTranslations);
                }
                reverseTranslations.add(name);
            }
        }
    }

    private DictionaryEntry getFirstEntryWithPrefix(String searchTerm) {
        for (Iterator it = entries.iterator(); it.hasNext();) {
            DictionaryEntry entry = (DictionaryEntry)it.next();
            if (entry.getName().toLowerCase().startsWith(searchTerm.toLowerCase())) {
                return entry;
            }
        }
        return null;
    }

    public DictionaryEntry getMatchingEntry(String searchTerm) {
        for (Iterator it = entries.iterator(); it.hasNext();) {
            DictionaryEntry entry = (DictionaryEntry)it.next();
            if (entry.getName().equalsIgnoreCase(searchTerm)) {
                return entry;
            }
        }
        return null;
    }

    public int getFirstEntryWithPrefixIndex(String searchTerm) {
        DictionaryEntry entry = getFirstEntryWithPrefix(searchTerm);
        if (entry != null) {
            return entries.indexOf(entry);
        }
        return -1;
    }
}