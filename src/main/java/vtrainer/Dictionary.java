package vtrainer;

import java.text.Collator;
import java.util.*;
import java.util.logging.Logger;

import com.google.common.collect.Iterables;
import org.jdom.Element;

public class Dictionary {

    // minimum time between testing the same word (in ms)
    // currently 15 minutes
    private static final int MIN_TEST_DISTANCE = 1000 * 60 * 15;
    private static final Logger logger = Logger.getLogger(Dictionary.class.getSimpleName());
    private static Random random = new Random(System.currentTimeMillis());
    private List<DictionaryEntry> entries = new ArrayList<>();
    private int highscore = 0;
    private Map<String, List<String>> reverseMap = new HashMap<>();
    private Map<String, DictionaryEntry> forwardMap = new HashMap<>();
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

    public List<DictionaryEntry> getEntries() {
        return entries;
    }

    public void addEntry(DictionaryEntry d) {
        d.setID(getNextID());
        entries.add(d);
        Collections.sort(entries, new Comparator() {
            public int compare(Object o1, Object o2) {
                DictionaryEntry d1 = (DictionaryEntry) o1;
                DictionaryEntry d2 = (DictionaryEntry) o2;

                return collator.compare(d1.getName(), d2.getName());
            }
        });
        refreshMaps();
    }

    private Integer getNextID() {
        Integer maxID = new Integer(0);
        for (Iterator<DictionaryEntry> it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry d = it.next();
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

        for (Iterator it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry de = (DictionaryEntry) it.next();
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

        for (Iterator it = e.getChildren("dictionary-entry").iterator(); it.hasNext(); ) {
            d.getEntries().add(DictionaryEntry.createFromXML((Element) it.next()));
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

    public Map<String, List<String>> getReverseMap() {
        return reverseMap;
    }

    public Map<String, DictionaryEntry> getForwardMap() {
        return forwardMap;
    }

    private static class ListCandidate implements Comparable<ListCandidate> {

        private final DictionaryEntry entry;

        public ListCandidate(DictionaryEntry entry) {
            this.entry = entry;
            sortRank = random.nextInt(30) + entry.getDifficulty();
        }

        // this is used for sorting, a bit of random + the difficulty as a boost
        private final int sortRank;

        @Override
        public int compareTo(ListCandidate o) {
            return Integer.valueOf(sortRank).compareTo(o.sortRank);
        }
    }

    public List<DictionaryEntry> createRandomList(int size) {

        List<DictionaryEntry> copy = new ArrayList<>(entries);
        // first eliminate all entries that have been asked recently
        long lastAllowed = System.currentTimeMillis() - MIN_TEST_DISTANCE;
        Iterables.removeIf(copy, (e) -> e.getLastTested() > lastAllowed);
        Iterables.removeIf(copy, (e) -> e.getCreated() > lastAllowed);

        // special case for small dictionaries: if not enough entries remain we suspend the
        // last tested criterion
        if (size > copy.size()) {
            logger.info("Not enough candidates not tested recently, using full list");
            copy = new ArrayList<>(entries);
        }

        if (size > copy.size()) {
            size = copy.size();
        }

        // build candidate list
        List<ListCandidate> listCandidates = new ArrayList<>();
        Iterables.addAll(listCandidates, Iterables.transform(copy, (e) -> new ListCandidate(e)));
        Collections.shuffle(listCandidates, random);
        Collections.sort(listCandidates);
        Collections.reverse(listCandidates);
        List<DictionaryEntry> randomList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            randomList.add(listCandidates.get(i).entry);
        }
        return randomList;
    }

    public int getAverageDifficulty() {
        int sum = 0;

        int avg = 0;

        if (entries.size() > 0) {
            for (Iterator iter = entries.iterator(); iter.hasNext(); ) {
                DictionaryEntry entry = (DictionaryEntry) iter.next();
                sum += entry.getDifficulty();
            }
            avg = (int) ((double) sum / (double) entries.size());
        }

        return avg;
    }

    public void refreshMaps() {
        forwardMap.clear();
        reverseMap.clear();
        for (Iterator<DictionaryEntry> it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry entry = it.next();
            String name = entry.getName();
            forwardMap.put(name, entry);
            for (Iterator itt = entry.getTranslations().iterator(); itt.hasNext(); ) {
                String translation = (String) itt.next();
                List reverseTranslations = (List) reverseMap.get(translation);
                if (reverseTranslations == null) {
                    reverseTranslations = new ArrayList();
                    reverseMap.put(translation, reverseTranslations);
                }
                reverseTranslations.add(name);
            }
        }
    }

    private DictionaryEntry getFirstEntryWithPrefix(String searchTerm) {
        for (Iterator<DictionaryEntry> it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry entry = it.next();
            if (entry.getName().toLowerCase().startsWith(searchTerm.toLowerCase())) {
                return entry;
            }
        }
        return null;
    }

    private DictionaryEntry getFirstEntryContaining(String searchTerm) {
        for (Iterator<DictionaryEntry> it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry entry = it.next();
            if (entry.getName().toLowerCase().contains(searchTerm.toLowerCase())) {
                return entry;
            }
        }
        return null;
    }

    public DictionaryEntry getMatchingEntry(String searchTerm) {
        for (Iterator<DictionaryEntry> it = entries.iterator(); it.hasNext(); ) {
            DictionaryEntry entry = it.next();
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

    public int getFirstEntryContainingIndex(String searchTerm) {
        DictionaryEntry entry = getFirstEntryContaining(searchTerm);
        if (entry != null) {
            return entries.indexOf(entry);
        }
        return -1;
    }
}