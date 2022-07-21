package vtrainer.gui;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import net.miginfocom.swing.MigLayout;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import vtrainer.Dictionary;
import vtrainer.DictionaryEntry;
import vtrainer.util.DocumentChangeListener;
import vtrainer.util.IntEditor;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyEditorManager;
import java.io.*;
import java.text.Collator;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

public class VTrainer {

    private final static Logger logger = Logger.getLogger(VTrainer.class.getSimpleName());
    // the size of the dictionary is divided by this to determine the number of words per test
    private static final int TEST_SET_SIZE_FACTOR = 200;
    private static final int MAX_TEST_SIZE = 5;
    private static final String DEFAULT_DICTIONARY_FILE_NAME = "vtrainer.xml";
    private static final int TEST_SET_SIZE = 5;
    // a test is only started after this minimum number of entries have been made
    private static final int MIN_DICTIONARY_SIZE = 10;
    // the difficulty value is increased by this for each test failure for the respective word
    private static final int INC_DIFFICULTY_PER_FAILURE = 2;
    // the difficulty value is decreased by this for each correct answer for the respective word in a test
    private static final int DEC_DIFFICULTY_PER_CORRECT_ANSWER = 2;
    private static final int DEC_DIFFICULTY_PER_I_REALLY_KNOW_ANSWER = 10;
    private static final int IOBUFSIZE = 20000;
    private static final int FLASH_DEFAULT_INTERVAL = 400;
    public static final String LABEL_WORD_PHRASE = "Word/Phrase";
    public static final String LABEL_TRANSLATION = "Translation";
    public static final String LABEL_TRANSLATIONS = "Translation(s)";
    public static final String LABEL_TAGS = "Tags";

    private final JFrame mainFrame;
    private final File dictionaryFile;
    private Dictionary dictionary = null;
    private DictionaryEntry selectedEntry = null;
    private DictionaryEntry testedEntry = null;
    private boolean testInverseDirection;
    private Action saveAction = null;
    private Action addEntryAction = null;
    private Action editEntryAction = null;
    private Action removeEntryAction = null;
    private Action removeTranslationAction = null;
    private Action exitAction = null;
    private Action startTestAction = null;
    private Action attemptHighscoreAction = null;
    private Action showReverseTranslationTableAction = null;
    private Action startFlashAction = null;
    private Action stopFlashAction = null;
    private Action editPropertiesAction = null;
    private EditDictionaryEntryDialog editDictionaryEntryDialog = null;
    private TestDialog testDialog = null;
    private TranslationDialog translationDialog = null;
    private FlashLearnerDialog flashDialogue = null;
    //    private PropertyDialog propertyDialog = null;

    private DefaultListModel dictionaryLM = new DefaultListModel();
    private JList dictionaryLI = null;
    private JTextField scoreTF = null;
    private JTextField searchTF = null;
    private JPopupMenu entryMenu = null;
    private JPopupMenu translationMenu = null;
    private String selectedTranslation = null;
    private List testSet = null;
    private int numCorrect = 0;

    private int flashInterval;

    private boolean highscoreMode = false;
    private static final int NEW_ENTRY_AVG_DIFFICULTY_DELTA = 4;
    private static final String VERSION = "0.9";

    static {
        //propertyeditors registrieren
        PropertyEditorManager.registerEditor(Integer.TYPE, IntEditor.class);
        // FIXME: use old sort because our random sorter violates the contract and causes exception
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        // make cmd-q emit a window event on mac
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
    }

    public VTrainer(File dictionaryFile) {
        this.dictionaryFile = dictionaryFile;

        if (dictionaryFile == null) {
            throw new IllegalArgumentException("null dictionary file");
        }

        mainFrame = new JFrame("VTrainer");

        initActions();
        initMenus();

        if (!dictionaryFile.exists()) {
            logger.info("dictionary file " + dictionaryFile
                    + " does not exist, creating empty one");
            dictionary = new Dictionary();
            try {
                save();
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        } else {
            try {
                load();
            } catch (Exception ex) {
                throw new RuntimeException("Error loading state", ex);
            }
        }

        flashInterval = dictionary.getFlashInterval() > 0 ? dictionary.getFlashInterval() : FLASH_DEFAULT_INTERVAL;

        editDictionaryEntryDialog = new EditDictionaryEntryDialog(mainFrame);
        testDialog = new TestDialog(mainFrame);
        translationDialog = new TranslationDialog(mainFrame);
        flashDialogue = new FlashLearnerDialog(mainFrame);
        //       propertyDialog = new PropertyDialog();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new MigLayout("fill, insets 0"));

        mainPanel.add(new JLabel("Dictionary"), "span, wrap");
        searchTF = new JTextField(20);
        mainPanel.add(searchTF, "span, growx, wrap");

        dictionaryLI = new JList(dictionaryLM);
        DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();
        dictionaryLI.setCellRenderer((list, value, index, isSelected, cellHasFocus) ->
                defaultRenderer.getListCellRendererComponent(list, ((DictionaryEntry) value).toSummaryString(), index, isSelected, cellHasFocus));
        dictionaryLI.addMouseListener(new MouseAdapter() {

            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelectedEntry();
                }
            }

            public void mousePressed(MouseEvent e) {
                checkForPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                checkForPopup(e);
            }

            private void checkForPopup(MouseEvent e) {
                int index = dictionaryLI.locationToIndex(e.getPoint());
                if (index >= 0) {
                    selectedEntry = (DictionaryEntry) dictionaryLM.get(index);
                    if (e.isPopupTrigger()) {
                        entryMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

        });

        dictionaryLI.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                selectedEntry = (DictionaryEntry) dictionaryLI.getSelectedValue();
            }
        });

        JScrollPane dScrollPane = new JScrollPane(dictionaryLI);
        dScrollPane.setPreferredSize(new Dimension(300, 400));
        mainPanel.add(dScrollPane, "span, wrap");
        JButton startTestBT = new JButton(startTestAction);
        mainPanel.add(startTestBT, "span, growx, wrap");

        searchTF.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                logger.info(e.toString());
                rebuildModel();
/*
                String searchTerm = searchTF.getText();
                int matchingIndex = dictionary.getFirstEntryWithPrefixIndex(searchTerm);
                if (matchingIndex != -1) {
                    searchTF.setForeground(Color.BLACK);
                    int last = dictionary.getEntries().size() - 1;
                    int numVisibleEntries = dictionaryLI.getLastVisibleIndex()
                            - dictionaryLI.getFirstVisibleIndex();
                    int computedIndex = Math.min(last, matchingIndex + numVisibleEntries - 1);
                    logger.info(searchTerm + " matching:" + matchingIndex + "("
                            + dictionary.getEntries().get(matchingIndex) + "), computed:"
                            + computedIndex);
                    // hack
                    dictionaryLI.ensureIndexIsVisible(0);
                    dictionaryLI.ensureIndexIsVisible(computedIndex);
                } else {
                    searchTF.setForeground(Color.RED);
                }
*/
            }
        });

        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        startTestBT.requestFocus();

        scoreTF = new JTextField(20);
        scoreTF.setEditable(false);
        mainPanel.add(scoreTF, "span, growx, wrap");

        refreshDictionary();

        mainFrame.getContentPane().add(mainPanel);
        mainFrame.pack();
        mainFrame.setLocation(new Point(300, 200));
        mainFrame.setVisible(true);
        mainFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                checkExit();
                System.exit(0);
            }
        });
    }

    private void setHighscoreMode(boolean m) {
        highscoreMode = m;
    }

    private void setNewHighscore(int highscore) {
        dictionary.setHighscore(highscore);
        saveAction.setEnabled(true);
    }

    private void initActions() {
        saveAction = new AbstractAction("Save") {
            public void actionPerformed(ActionEvent ae) {
                try {
                    save();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
            }
        };

        saveAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));

        addEntryAction = new AbstractAction("Add Entry") {
            public void actionPerformed(ActionEvent ae) {
                addEntry();
            }
        };

        addEntryAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));

        editEntryAction = new AbstractAction("Edit Entry") {
            public void actionPerformed(ActionEvent ae) {
                editDictionaryEntryDialog.refresh();
                editDictionaryEntryDialog.setVisible(true);
            }
        };

        removeEntryAction = new AbstractAction("Remove Entry") {
            public void actionPerformed(ActionEvent ae) {
                int option = JOptionPane.showConfirmDialog(mainFrame, "remove " + selectedEntry
                        + " from dictionary", "remove entry", JOptionPane.YES_NO_OPTION);
                if (option == JOptionPane.YES_OPTION) {
                    dictionary.getEntries().remove(selectedEntry);
                    selectedEntry = null;
                    refreshDictionary();
                    saveAction.setEnabled(true);
                }
            }
        };

        removeTranslationAction = new AbstractAction("Remove Translation") {
            public void actionPerformed(ActionEvent ae) {
                selectedEntry.getTranslations().remove(selectedTranslation);
                editDictionaryEntryDialog.refreshTranslations();
                saveAction.setEnabled(true);
            }
        };

        exitAction = new AbstractAction("Exit") {
            public void actionPerformed(ActionEvent ae) {
                if (checkExit()) {
                    System.exit(0);
                }
            }
        };

        exitAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_MASK));

        startTestAction = new AbstractAction("Start Test") {
            public void actionPerformed(ActionEvent ae) {
                startTest();
            }
        };

        startTestAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK));

        attemptHighscoreAction = new AbstractAction("Attempt Highscore") {
            public void actionPerformed(ActionEvent ae) {
                setHighscoreMode(true);
                startTest();
                setHighscoreMode(false);
            }
        };
        attemptHighscoreAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_MASK));

        showReverseTranslationTableAction = new AbstractAction("Show Reverse Translations") {
            public void actionPerformed(ActionEvent ae) {
                translationDialog.setData(dictionary.getReverseMap());
                translationDialog.show();
            }
        };

        startFlashAction = new AbstractAction("Start Reverse Flash") {
            public void actionPerformed(ActionEvent ae) {
                startFlash();
            }
        };

    }

    private boolean checkExit() {
        if (saveAction.isEnabled()) {
            int option = JOptionPane.showConfirmDialog(mainFrame, "Save dictionary",
                    "Save", JOptionPane.YES_NO_CANCEL_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                try {
                    save();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(mainFrame, ex.toString(), "Save failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else if (option == JOptionPane.CANCEL_OPTION) {
                return false;
            }
        }

        return true;
    }

    private void editSelectedEntry() {
        if (selectedEntry != null) {
            editDictionaryEntryDialog.refresh();
            editDictionaryEntryDialog.setVisible(true);
        }
    }

    private void addEntry() {
        selectedEntry = new DictionaryEntry();
        selectedEntry.setCreated(System.currentTimeMillis());
        editDictionaryEntryDialog.refresh();
        editDictionaryEntryDialog.setVisible(true);
        saveAction.setEnabled(true);
    }

    private void startTest() {

        int testSize = Math.min(TEST_SET_SIZE
                * (dictionary.getEntries().size() / TEST_SET_SIZE_FACTOR + 1), MAX_TEST_SIZE);

        if (highscoreMode) {
            testSize = dictionary.getEntries().size();
        }

        testSet = dictionary.createRandomList(testSize);
        System.err.println("starting test with set (" + testSet.size() + "):" + testSet);
        getNextTestEntry();

        numCorrect = 0;

        testDialog.refresh();
        testDialog.setLocation(mainFrame.getLocation());
        testDialog.setVisible(true);

        saveAction.setEnabled(true);
    }

    private void startFlash() {
        flashDialogue.setLocation(mainFrame.getX(), mainFrame.getY());
        flashDialogue.newFlash();
        flashDialogue.setVisible(true);
    }

    private DictionaryEntry getNextTestEntry() {
        if (testSet.isEmpty())
            return null;

        testedEntry = (DictionaryEntry) testSet.remove(0);

        testInverseDirection = !testInverseDirection;

        return testedEntry;
    }

    private void save() throws IOException {
        logger.info("saving dictionary to " + dictionaryFile);
        if (dictionaryFile.exists()) {
            File backup = new File(dictionaryFile.getCanonicalPath() + ".vtbak");
            try {
                BufferedOutputStream backupOutputStream = new BufferedOutputStream(
                        new FileOutputStream(backup), IOBUFSIZE);
                ByteStreams.copy(new BufferedInputStream(new FileInputStream(dictionaryFile),
                        IOBUFSIZE), backupOutputStream);
                backupOutputStream.close();
                logger.info("created backup in " + backup);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "creating backup in " + backup
                        + " failed " + ex, "backup error", JOptionPane.WARNING_MESSAGE);
            }
        }
        Document doc = new Document(dictionary.toXML());
        XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
        xmlOutputter.output(doc, new OutputStreamWriter(new BufferedOutputStream(
                new FileOutputStream(dictionaryFile), IOBUFSIZE), "UTF-8"));

        saveAction.setEnabled(false);
    }

    private void load() throws IOException, JDOMException {
        System.err.println("loading dictionary from " + dictionaryFile);
        SAXBuilder builder = new SAXBuilder(false);
        Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(
                dictionaryFile), IOBUFSIZE), "UTF-8");
        Document doc = builder.build(reader);
        dictionary = Dictionary.createFromXML(doc.getRootElement());
        reader.close();
        saveAction.setEnabled(false);
    }

    private void initMenus() {
        JMenuBar mbar = new JMenuBar();
        JMenu doMenu = new JMenu("VTrainer");
        doMenu.add(saveAction);
        doMenu.add(addEntryAction);
        doMenu.addSeparator();
        doMenu.add(startTestAction);
        doMenu.add(attemptHighscoreAction);
        doMenu.addSeparator();
        doMenu.add(showReverseTranslationTableAction);
        doMenu.add(startFlashAction);
        doMenu.addSeparator();
        doMenu.add(exitAction);
        mbar.add(doMenu);

        JMenu helpMenu = new JMenu("?");
        helpMenu.add(new AbstractAction("About") {
            public void actionPerformed(ActionEvent ae) {
                String msg = "VTrainer v" + VERSION + "\n\n"
                        + "Copyright 2002 - 2022 Robert Krüger";
                JOptionPane.showMessageDialog(mainFrame, msg, "About VTrainer",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        mbar.add(helpMenu);

        mainFrame.setJMenuBar(mbar);

        entryMenu = new JPopupMenu();
        entryMenu.add(editEntryAction);
        entryMenu.add(removeEntryAction);
        entryMenu.add(addEntryAction);

        translationMenu = new JPopupMenu();
        translationMenu.add(removeTranslationAction);

    }

    private void checkCanAddEntry() {
        addEntryAction.setEnabled(dictionary.getEntries().size() < MIN_DICTIONARY_SIZE);
    }

    private void checkCanStartTest() {
        startTestAction.setEnabled(dictionary.getEntries().size() >= MIN_DICTIONARY_SIZE);
    }

    private void refreshDictionary() {
        checkCanAddEntry();
        checkCanStartTest();
        rebuildModel();

        scoreTF.setText("" + dictionary.getEntries().size() + " entries, highscore "
                + dictionary.getHighscore());

        dictionary.refreshMaps();
    }

    private void rebuildModel() {
        dictionaryLM.removeAllElements();

        List<DictionaryEntry> entries = dictionary.getEntries();

        String searchText = searchTF.getText().trim().toLowerCase();
        logger.info("Rebuilding model with search term '" + searchText + "'");

        entries.stream().filter(e -> {
            if (!Strings.isNullOrEmpty(searchText)) {
                if (e.getName().toLowerCase().contains(searchText)) {
                    return true;
                }
                if(e.getTranslations().stream().anyMatch(t -> t.toLowerCase().contains(searchText))){
                    return true;
                }
                return false;
            }
            return true;
        }).forEach(e -> dictionaryLM.addElement(e));
    }

    public static void main(String[] argv) throws Exception {
        logger.info("starting VTrainer");

        //        try {
        //            UIManager.setLookAndFeel(new
        // com.incors.plaf.kunststoff.KunststoffLookAndFeel());
        //        } catch (UnsupportedLookAndFeelException ex) {
        //        }

        File dictFile = null;
        if (argv.length > 0) {
            dictFile = new File(argv[0]);
        }

        if (dictFile == null) {
            dictFile = new File(new File(System.getProperty("user.home")), DEFAULT_DICTIONARY_FILE_NAME);
            System.err.println("no dictionary file specified, defaulting to " + dictFile);
        }

        new VTrainer(dictFile);
    }

    private class EditDictionaryEntryDialog extends JDialog {
        private JTextField nameTF = new JTextField(20);
        private JTextArea notesTA = new JTextArea(5, 30);
        private JTextField translationTF = new JTextField(20);
        private JList translationLI = null;

        private JTextField tagsTF = new JTextField(20);
        private DefaultListModel translationLM = new DefaultListModel();
        private JButton addBT = new JButton("Add Translation");
        private JButton okBT = new JButton("OK");
        private JButton cancelBT = new JButton("Cancel");

        public EditDictionaryEntryDialog(JFrame ownerFrame) {
            super(ownerFrame, "Edit Dictionary Entry", true);
            JPanel basePanel = new JPanel();
            basePanel.setLayout(new BorderLayout());

            nameTF.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    checkCanSubmit();
                    checkCanAddTranslation();
                }
            });

            translationTF.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    checkCanAddTranslation();
                }
            });

            final ActionListener addListener = e -> {
                if (addBT.isEnabled()) {
                    addTranslation();
                } else if (!selectedEntry.getTranslations().isEmpty()) {
                    submit();
                }
            };
            translationTF.addActionListener(addListener);

            addBT.addActionListener(addListener);

            cancelBT.addActionListener(e -> hide());

            okBT.addActionListener(e -> {
                if (translationTF.getText().length() > 0) {
                    addTranslation();
                }
                submit();
            });

            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
            topPanel.add(Box.createHorizontalGlue());
            topPanel.add(new JLabel(LABEL_WORD_PHRASE));
            topPanel.add(nameTF);
            topPanel.add(Box.createHorizontalStrut(20));
            topPanel.add(new JLabel(LABEL_TRANSLATION));
            topPanel.add(translationTF);
            topPanel.add(Box.createHorizontalGlue());
            topPanel.add(addBT);

            basePanel.add(topPanel, BorderLayout.NORTH);
            translationLI = new JList(translationLM);

            translationLI.addMouseListener(new MouseAdapter() {

                public void mousePressed(MouseEvent e) {
                    checkForPopup(e);
                }

                public void mouseReleased(MouseEvent e) {
                    checkForPopup(e);
                }

                private void checkForPopup(MouseEvent e) {
                    selectedTranslation = (String) translationLM.get(translationLI.locationToIndex(e
                            .getPoint()));
                    if (e.isPopupTrigger()) {
                        translationMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });

            JScrollPane tScrollPane = new JScrollPane(translationLI);
            tScrollPane.setPreferredSize(new Dimension(200, 100));

            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.X_AXIS));

            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BorderLayout());
            listPanel.add(new JLabel(LABEL_TRANSLATIONS), BorderLayout.NORTH);
            listPanel.add(tScrollPane, BorderLayout.CENTER);

            centerPanel.add(listPanel);
            centerPanel.add(Box.createHorizontalStrut(20));

            JPanel notes = new JPanel();
            notes.setLayout(new BorderLayout());
            notes.add(new JLabel("Notes"), BorderLayout.NORTH);

            JScrollPane nScrollPane = new JScrollPane(notesTA);
            nScrollPane.setPreferredSize(new Dimension(200, 100));

            notes.add(nScrollPane, BorderLayout.CENTER);

            centerPanel.add(notes);

            basePanel.add(centerPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

            JPanel tagsUIPanel = new JPanel();
            tagsUIPanel.setLayout(new FlowLayout());
            tagsUIPanel.add(new JLabel(LABEL_TAGS));
            tagsUIPanel.add(tagsTF);
            tagsUIPanel.setMaximumSize(tagsUIPanel.getPreferredSize());

            bottomPanel.add(tagsUIPanel);
            bottomPanel.add(Box.createHorizontalGlue());
            bottomPanel.add(okBT);
            bottomPanel.add(cancelBT);

            basePanel.add(bottomPanel, BorderLayout.SOUTH);

            basePanel.setBorder(BorderFactory.createTitledBorder("edit dictionary entry"));
            getContentPane().add(basePanel);

            pack();
        }

        void checkCanAddTranslation() {
            addBT.setEnabled(nameTF.getText().length() > 0 && translationTF.getText().length() > 0);
        }

        void checkCanSubmit() {

            String inputName = nameTF.getText();
            boolean completeData = inputName.length() > 0
                    && !selectedEntry.getTranslations().isEmpty();

            okBT.setEnabled(completeData);
            nameTF.setForeground(Color.BLACK);

            if (inputName.length() > 0) {
                boolean duplicateNew = selectedEntry.getID() == null
                        && dictionary.getMatchingEntry(inputName) != null;
                if (duplicateNew) {
                    nameTF.setForeground(Color.RED);
                    okBT.setEnabled(false);
                }
            }
        }

        void addTranslation() {
            final String text = translationTF.getText();
            // allow comma-separated translations
            final String[] components = text.split(",");
            for (String comp : components) {
                comp = comp.trim();
                if (!Strings.isNullOrEmpty(comp)) {
                    selectedEntry.getTranslations().add(comp);
                }
            }
            translationTF.setText("");
            refreshTranslations();
            checkCanSubmit();
            saveAction.setEnabled(true);
        }

        void submit() {
            selectedEntry.setName(nameTF.getText());

            if (notesTA.getText().length() > 0) {
                selectedEntry.setNotes(notesTA.getText());
            }

            String tags = tagsTF.getText();
            if (tags.length() > 0) {
                String[] tagComponents = tags.split(",");
                for (String tag : tagComponents) {
                    tag = tag.trim();
                    if (!Strings.isNullOrEmpty(tag)) {
                        selectedEntry.getTags().add(tag);
                    }
                }
            }

            if (selectedEntry.getID() == null) {
                dictionary.addEntry(selectedEntry);
                refreshDictionary();
            }

            //saveAction.setEnabled(true);
            try {
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
            setVisible(false);
        }

        void refreshTranslations() {
            translationLM.removeAllElements();

            if (selectedEntry != null) {
                for (Iterator it = selectedEntry.getTranslations().iterator(); it.hasNext(); ) {
                    translationLM.addElement(it.next());
                }
            }
        }

        void refresh() {
            if (selectedEntry != null && selectedEntry.getName() != null) {
                nameTF.setText(selectedEntry.getName());
            } else {
                nameTF.setText("");
            }

            if (selectedEntry != null && selectedEntry.getNotes() != null) {
                notesTA.setText(selectedEntry.getNotes());
            } else {
                notesTA.setText("");
            }

            if (selectedEntry != null && selectedEntry.getTags() != null) {
                tagsTF.setText(String.join(",", selectedEntry.getTags()));
            } else {
                tagsTF.setText("");
            }

            translationTF.setText("");

            refreshTranslations();

            checkCanSubmit();

            addBT.setEnabled(false);

            nameTF.requestFocus();
        }
    }

    private class TranslationDialog extends JDialog {

        private DefaultTableModel model = new DefaultTableModel();

        public TranslationDialog(JFrame ownerFrame) {
            super(ownerFrame, "Reverse Translations", true);
            JPanel basePanel = new JPanel();
            basePanel.setLayout(new BorderLayout());

            JScrollPane scrollPane = new JScrollPane(new JTable(model)) {
                public Dimension getPreferredSize() {
                    return new Dimension(300, 500);
                }
            };

            basePanel.add(scrollPane, BorderLayout.CENTER);
            getContentPane().add(basePanel);
            pack();
        }

        public void setData(Map data) {
            List sortedKeys = new ArrayList(data.keySet());
            Collator collator = dictionary.getCollator();
            Collections.sort(sortedKeys, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return collator.compare(((String) o1).toLowerCase(), ((String) o2).toLowerCase());
                }
            });

            Vector rows = new Vector();

            for (Iterator it = sortedKeys.iterator(); it.hasNext(); ) {
                String key = (String) it.next();
                List translations = (List) data.get(key);
                for (int i = 0; i < translations.size(); i++) {
                    String translation = (String) translations.get(i);
                    Vector row = new Vector();
                    if (i == 0) {
                        row.add(key);
                    } else {
                        row.add("");
                    }
                    row.add(translation);
                    rows.add(row);
                }
            }

            Vector labels = new Vector();
            labels.add("Word/Phrase");
            labels.add("Translation");
            model.setDataVector(rows, labels);
        }

    }

    private class TestDialog extends JDialog {
        private JTextField nameTF = new JTextField(20);
        private JTextField inputTF = new JTextField(20);
        private JButton okBT = new JButton("OK");
        private JButton cancelBT = new JButton("cancel");
        private JButton correctBT = new JButton("I know");
        private JButton dunnoBT = new JButton("I don't know");
        private JButton correctDamnBT = new JButton("I really know");
        private JButton showBT = new JButton("show");
        private JTextArea previewTextArea = new JTextArea(3, 30);

        public TestDialog(JFrame ownerFrame) {
            super(ownerFrame, "Test", true);
            JPanel basePanel = new JPanel();
            basePanel.setLayout(new BorderLayout());

            nameTF.setEditable(false);

            correctBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit(true, false);
                }
            });
            dunnoBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit(false, false);
                }
            });
            correctDamnBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit(true, true);
                }
            });

            inputTF.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    okBT.setEnabled(inputTF.getText().length() > 0);
                }
            });

            inputTF.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit(false, false);
                }
            });

            cancelBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setHighscoreMode(false);
                    setVisible(false);
                }
            });

            okBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit(false, false);
                }
            });

            showBT.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    previewTextArea.setText(testInverseDirection ? testedEntry.getName() : String.join(System.getProperty("line.separator"), testedEntry.getTranslations()));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    previewTextArea.setText("");
                }
            });

            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
            topPanel.add(Box.createHorizontalGlue());
            topPanel.add(new JLabel("Word/Phrase"));
            topPanel.add(nameTF);
            topPanel.add(Box.createHorizontalStrut(20));
            topPanel.add(new JLabel("Translation"));
            topPanel.add(inputTF);
            topPanel.add(Box.createHorizontalGlue());
            basePanel.add(topPanel, BorderLayout.NORTH);

            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
            bottomPanel.add(Box.createHorizontalGlue());
            bottomPanel.add(previewTextArea);
            bottomPanel.add(showBT);
            bottomPanel.add(correctBT);
            bottomPanel.add(correctDamnBT);
            bottomPanel.add(dunnoBT);
            bottomPanel.add(okBT);
            bottomPanel.add(cancelBT);

            basePanel.add(bottomPanel, BorderLayout.SOUTH);

            basePanel.setBorder(BorderFactory.createTitledBorder("enter translation"));
            getContentPane().add(basePanel);

            pack();

        }

        void refresh() {
            setTitle("Test, score:" + numCorrect);
            nameTF.setText(testInverseDirection ? testedEntry.getRandomTranslation() : testedEntry.getName());
            inputTF.setText("");
            inputTF.requestFocus();
        }

        void submit(boolean assumeCorrect, boolean reallyKnown) {
            testedEntry.setLastTested(System.currentTimeMillis());
            if (assumeCorrect || inputIsTranslation()) {
                testedEntry.addToDifficulty(reallyKnown ? -DEC_DIFFICULTY_PER_I_REALLY_KNOW_ANSWER : -DEC_DIFFICULTY_PER_CORRECT_ANSWER);
                numCorrect++;
                JOptionPane.showMessageDialog(mainFrame, testedEntry.toLargeString(), "correct",
                        JOptionPane.INFORMATION_MESSAGE);
                DictionaryEntry entry = getNextTestEntry();
                if (entry == null) {
                    if (highscoreMode) {
                        JOptionPane.showMessageDialog(mainFrame,
                                "you bastard know the entire dictionary!", "full score",
                                JOptionPane.INFORMATION_MESSAGE);
                        setNewHighscore(dictionary.getEntries().size());
                        refreshDictionary();
                    } else {
                        JOptionPane.showMessageDialog(mainFrame,
                                "well done! you may now add a word to the dictionary",
                                "test passed", JOptionPane.INFORMATION_MESSAGE);
                        setVisible(false);
                        addEntry();
                    }
                }
                refresh();
            } else {
                JOptionPane.showMessageDialog(mainFrame, testedEntry.toLargeString(),
                        "test failed", JOptionPane.ERROR_MESSAGE);

                testedEntry.addToDifficulty(INC_DIFFICULTY_PER_FAILURE);
                if (highscoreMode) {
                    int numCorrectEntries = dictionary.getEntries().size() - testSet.size() - 1;
                    if (numCorrectEntries > dictionary.getHighscore()) {
                        setNewHighscore(numCorrectEntries);
                        refreshDictionary();
                        JOptionPane.showMessageDialog(mainFrame, "new highscore is "
                                        + numCorrectEntries, "new highscore",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(mainFrame, "highscore remains at "
                                        + dictionary.getHighscore(), "no new highscore",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }

                setVisible(false);
            }
        }

        private boolean inputIsTranslation() {
            String input = inputTF.getText();
            return testInverseDirection ? input.equalsIgnoreCase(testedEntry.getName()) : testedEntry.isTranslation(input);
        }
    }

    private class FlashLearnerDialog extends JDialog {

        private JTextField wordTF = new JTextField(30);
        private JTextField countTF = new JTextField(10);
        private JTextArea translationsTA = new JTextArea(5, 30);
        private JSlider intervalSlider = new JSlider(100, 4000, flashInterval);
        private Iterator translationIterator = null;
        private List<Map.Entry<String, List<String>>> wordList = null;

        private int currentIndex = 0;

        private Timer flashTimer = new Timer(flashInterval, ae -> next());

        public FlashLearnerDialog(JFrame ownerFrame) {
            super(ownerFrame, "Reverse Translation Flash", true);
            wordTF.setFont(new Font(getFont().getName(), Font.BOLD, 36));
            wordTF.setEditable(false);
            countTF.setEditable(false);
            translationsTA.setFont(new Font(getFont().getName(), Font.PLAIN, 36));
            translationsTA.setEditable(false);

            JPanel basePanel = new JPanel();
            BorderLayout mainLayout = new BorderLayout();
            mainLayout.setHgap(5);
            mainLayout.setVgap(5);
            basePanel.setLayout(mainLayout);
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            JLabel wordLB = new JLabel("Word/Phrase");

            //mainPanel.add(wordLB);
            mainPanel.add(wordTF);
            mainPanel.add(countTF);
            JLabel translationsLB = new JLabel("Translation(s)");
            //mainPanel.add(translationsLB);
            mainPanel.add(new JScrollPane(translationsTA));

            basePanel.add(mainPanel, BorderLayout.CENTER);
            getContentPane().add(basePanel);

            JPanel controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));

            controlPanel.add(new JButton(new AbstractAction("start/pause") {
                public void actionPerformed(ActionEvent ae) {
                    pauseRestart();
                }
            }));
            controlPanel.add(new JButton(new AbstractAction("<") {
                public void actionPerformed(ActionEvent ae) {
                    previousEntry();
                }
            }));
            controlPanel.add(new JButton(new AbstractAction(">") {
                public void actionPerformed(ActionEvent ae) {
                    nextEntry();
                }
            }));

            controlPanel.add(new JButton(new AbstractAction("close") {
                public void actionPerformed(ActionEvent ae) {
                    close();
                }
            }));

            controlPanel.add(Box.createHorizontalStrut(10));
            controlPanel.add(new JLabel("Speed"));

            intervalSlider.setPaintLabels(false);
            intervalSlider.setPaintTicks(true);
            intervalSlider.setMajorTickSpacing(500);
            intervalSlider.setMinorTickSpacing(100);
            intervalSlider.setInverted(true);
            intervalSlider.setSnapToTicks(true);
            intervalSlider.addChangeListener(ce -> {
                flashInterval = intervalSlider.getValue();
                flashTimer.setDelay(flashInterval);
                dictionary.setFlashInterval(flashInterval);
                saveAction.setEnabled(true);
            });

            controlPanel.add(intervalSlider);

            basePanel.add(controlPanel, BorderLayout.SOUTH);

            pack();
        }

        private void pauseRestart() {
            if (flashTimer.isRunning()) {
                flashTimer.stop();
            } else {
                flashTimer.restart();
            }
        }

        public void newFlash() {
            Map<String, List<String>> reverseMap = dictionary.getReverseMap();
            wordList = new ArrayList(reverseMap.entrySet());
            Collections.shuffle(wordList);
            currentIndex = 0;
            setActiveEntry();
        }

        private void close() {
            flashTimer.stop();
            setVisible(false);
        }

        private void previousEntry() {
            if (currentIndex == 0) {
                return;
            }

            boolean wasRunning = flashTimer.isRunning();
            flashTimer.stop();
            currentIndex--;
            setActiveEntry();
            if (wasRunning) {
                flashTimer.restart();
            }
        }

        private void nextEntry() {
            if (currentIndex >= wordList.size() - 1) {
                flashTimer.stop();
                return;
            }

            boolean wasRunning = flashTimer.isRunning();
            flashTimer.stop();
            currentIndex++;
            setActiveEntry();
            if (wasRunning) {
                flashTimer.restart();
            }
        }

        private void setActiveEntry() {
            Entry entry = wordList.get(currentIndex);
            logger.info("Setting active entry to " + entry.getKey());
            wordTF.setText((String) entry.getKey());
            countTF.setText(currentIndex + 1 + "/" + wordList.size());
            translationIterator = ((List) entry.getValue()).iterator();
            translationsTA.setText("");
        }

        void next() {
            if (translationIterator != null && translationIterator.hasNext()) {
                translationsTA.append((String) translationIterator.next() + "\n");
            } else {
                nextEntry();
            }
        }
    }

}