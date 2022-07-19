package vtrainer.test;

import junit.framework.Assert;
import junit.framework.TestCase;
import vtrainer.Dictionary;
import vtrainer.DictionaryEntry;

public class BasicTest extends TestCase {

    private Dictionary dictionary = null;

    public BasicTest(String name) {
        super(name);
    }

    protected void setUp() {
        dictionary = new Dictionary();
        DictionaryEntry de = new DictionaryEntry();
        de.setName("problema");
        de.getTranslations().add("problem");
        dictionary.addEntry(de);

        de = new DictionaryEntry();
        de.setName("venir");
        de.getTranslations().add("kommen");
        de.getTranslations().add("gehen");
        dictionary.addEntry(de);
    }

    protected void tearDown() {
        dictionary = null;
    }

    public void testDataStructure() throws Exception {
        DictionaryEntry de = new DictionaryEntry();
        Assert.assertNull("new Dictionary entry has non-null ID", de.getID());

        de.setName("mano");
        de.getTranslations().add("hand");

        Assert.assertTrue("correct translation doesn't work", de.isTranslation("Hand"));
        Assert.assertTrue("incorrect translation does work", !de.isTranslation("bein"));
    }

}
