package vtrainer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyEditorManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import com.google.common.io.ByteStreams;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import vtrainer.Dictionary;
import vtrainer.DictionaryEntry;
import vtrainer.util.IntEditor;
import vtrainer.util.PropertySheet;

public class VTrainer {

    // the size of the dictionary is divided by this to determine the number of words per test
    private static final int TEST_SET_SIZE_FACTOR = 200;
    private static final int MAX_TEST_SIZE = 10;
    private static final String DEFAULT_DICTIONARY_FILE_NAME = "vtrainer.xml";
    private static final int TEST_SET_SIZE = 5;
    // a test is only started after this minimum number of entries have been made
    private static final int MIN_DICTIONARY_SIZE = 20;
    // the difficulty value is increased by this for each test failure for the respective word
    private static final int INC_DIFFICULTY_PER_FAILURE = 5;
    // the difficulty value is decreased by this for each correct answer for the respective word in a test
    private static final int DEC_DIFFICULTY_PER_CORRECT_ANSWER = 2;
    private static final int IOBUFSIZE = 20000;
    private static final int FLASH_INTERVAL = 400;

    private JFrame mainFrame = null;
    private File dictionaryFile = null;
    private Dictionary dictionary = null;
    private DictionaryEntry selectedEntry = null;
    private DictionaryEntry testedEntry = null;
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

    private int flashInterval = FLASH_INTERVAL;

    private boolean highscoreMode = false;
    private static final int NEW_ENTRY_AVG_DIFFICULTY_DELTA = 4;
    private static final String VERSION = "0.9";

    static {
        //propertyeditors registrieren
        PropertyEditorManager.registerEditor(Integer.TYPE, IntEditor.class);
        // FIXME: use old sort because our random sorter violates the contract and causes exception
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
    }

    public VTrainer(File dictionaryFile) {
        this.dictionaryFile = dictionaryFile;

        if (dictionaryFile == null) {
            throw new IllegalArgumentException("null dictionary file");
        }

        mainFrame = new JFrame("VTrainer");

        initActions();
        initMenus();

        editDictionaryEntryDialog = new EditDictionaryEntryDialog(mainFrame);
        testDialog = new TestDialog(mainFrame);
        translationDialog = new TranslationDialog(mainFrame);
        flashDialogue = new FlashLearnerDialog(mainFrame);
        //       propertyDialog = new PropertyDialog();

        if (!dictionaryFile.exists()) {
            System.err.println("dictionary file " + dictionaryFile
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
                throw new RuntimeException("arghh!!!:" + ex);
            }
        }

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());

        JPanel dictionaryPanel = new JPanel();
        dictionaryPanel.setLayout(new BorderLayout());

        JPanel dictionaryTopPanel = new JPanel();
        dictionaryTopPanel.setLayout(new BorderLayout());
        dictionaryTopPanel.add(new JLabel("Dictionary"), BorderLayout.NORTH);
        searchTF = new JTextField(20);
        dictionaryTopPanel.add(searchTF, BorderLayout.SOUTH);
        dictionaryPanel.add(dictionaryTopPanel, BorderLayout.NORTH);

        dictionaryLI = new JList(dictionaryLM);
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
                    selectedEntry = (DictionaryEntry)dictionaryLM.get(index);
                    if (e.isPopupTrigger()) {
                        entryMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

        });

        dictionaryLI.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                selectedEntry = (DictionaryEntry)dictionaryLI.getSelectedValue();
            }
        });

        JScrollPane dScrollPane = new JScrollPane(dictionaryLI);
        dScrollPane.setPreferredSize(new Dimension(200, 400));
        dictionaryPanel.add(dScrollPane, BorderLayout.CENTER);
        JButton startTestBT = new JButton(startTestAction);
        dictionaryPanel.add(startTestBT, BorderLayout.SOUTH);

        searchTF.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                System.err.println(e);
                String searchTerm = searchTF.getText();
                int matchingIndex = dictionary.getFirstEntryWithPrefixIndex(searchTerm);
                if (matchingIndex != -1) {
                    searchTF.setForeground(Color.BLACK);
                    int last = dictionary.getEntries().size() - 1;
                    int numVisibleEntries = dictionaryLI.getLastVisibleIndex()
                            - dictionaryLI.getFirstVisibleIndex();
                    int computedIndex = Math.min(last, matchingIndex + numVisibleEntries - 1);
                    System.err.println(searchTerm + " matching:" + matchingIndex + "("
                            + dictionary.getEntries().get(matchingIndex) + "), computed:"
                            + computedIndex);
                    // hack
                    dictionaryLI.ensureIndexIsVisible(0);
                    dictionaryLI.ensureIndexIsVisible(computedIndex);
                } else {
                    searchTF.setForeground(Color.RED);
                }
            }
        });

        mainPanel.add(dictionaryPanel, BorderLayout.WEST);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        startTestBT.requestFocus();

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        scoreTF = new JTextField(20);
        scoreTF.setEditable(false);
        bottomPanel.add(scoreTF);
        bottomPanel.add(Box.createHorizontalGlue());
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

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

        saveAction.putValue(AbstractAction.MNEMONIC_KEY, new Integer((int)'s'));
        saveAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(new Character(
                's'), InputEvent.CTRL_MASK));

        addEntryAction = new AbstractAction("Add Entry") {
            public void actionPerformed(ActionEvent ae) {
                addEntry();
            }
        };

        addEntryAction.putValue(AbstractAction.MNEMONIC_KEY, new Integer((int)'a'));

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

        exitAction.putValue(AbstractAction.MNEMONIC_KEY, new Integer((int)'x'));
        exitAction.putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(new Character(
                'x'), InputEvent.CTRL_MASK));

        startTestAction = new AbstractAction("Start Test") {
            public void actionPerformed(ActionEvent ae) {
                startTest();
            }
        };

        startTestAction.putValue(AbstractAction.MNEMONIC_KEY, new Integer((int)'t'));

        attemptHighscoreAction = new AbstractAction("Attempt Highscore") {
            public void actionPerformed(ActionEvent ae) {
                setHighscoreMode(true);
                startTest();
                setHighscoreMode(false);
            }
        };

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

        //        editPropertiesAction = new AbstractAction("edit properties") {
        //            public void actionPerformed(ActionEvent ae) {
        //                propertyDialog.show();
        //            }
        //        };

    }

    private boolean checkExit() {
        if (saveAction.isEnabled()) {
            int option = JOptionPane.showConfirmDialog(mainFrame, "Save dictionary and score",
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
        editDictionaryEntryDialog.refresh();
        editDictionaryEntryDialog.setVisible(true);
        selectedEntry.setDifficulty(dictionary.getAverageDifficulty()
                + NEW_ENTRY_AVG_DIFFICULTY_DELTA);
        System.err.println("average difficulty:" + dictionary.getAverageDifficulty());
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

        testedEntry = (DictionaryEntry)testSet.remove(0);

        return testedEntry;
    }

    private void save() throws IOException {
        System.err.println("saving dictionary to " + dictionaryFile);
        if (dictionaryFile.exists()) {
            File backup = new File(dictionaryFile.getCanonicalPath() + ".vtbak");
            try {
                BufferedOutputStream backupOutputStream = new BufferedOutputStream(
                        new FileOutputStream(backup), IOBUFSIZE);
                ByteStreams.copy(new BufferedInputStream(new FileInputStream(dictionaryFile),
                        IOBUFSIZE), backupOutputStream);
                backupOutputStream.close();
                System.err.println("created backup in " + backup);
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
        JMenu doMenu = new JMenu("Do Stuff");
        doMenu.add(saveAction);
        doMenu.add(addEntryAction);
        doMenu.addSeparator();
        doMenu.add(startTestAction);
        doMenu.add(attemptHighscoreAction);
        doMenu.addSeparator();
        doMenu.add(showReverseTranslationTableAction);
        doMenu.add(startFlashAction);
        doMenu.addSeparator();
        //        doMenu.add(editPropertiesAction);
        //        doMenu.addSeparator();
        doMenu.add(exitAction);
        mbar.add(doMenu);

        JMenu helpMenu = new JMenu("?");
        helpMenu.add(new AbstractAction("About") {
            public void actionPerformed(ActionEvent ae) {
                String msg = "VTrainer v" + VERSION + "\n\n"
                        + "Copyright 2002 - 2016 Robert Krueger";
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
        dictionaryLM.removeAllElements();

        List entries = dictionary.getEntries();

        for (Iterator it = entries.iterator(); it.hasNext();) {
            dictionaryLM.addElement(it.next());
        }

        scoreTF.setText("" + dictionary.getEntries().size() + " entries, highscore "
                + dictionary.getHighscore());

        dictionary.refreshMaps();
    }

    public static void main(String[] argv) throws Exception {
        System.out.println("starting VTrainer");

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
        private DefaultListModel translationLM = new DefaultListModel();
        private JButton addBT = new JButton("add translation");
        private JButton okBT = new JButton("OK");
        private JButton cancelBT = new JButton("cancel");

        public EditDictionaryEntryDialog(JFrame ownerFrame) {
            super(ownerFrame, "Edit Dictionary Entry", true);
            JPanel basePanel = new JPanel();
            basePanel.setLayout(new BorderLayout());

            nameTF.addKeyListener(new KeyAdapter() {
                // zur sicherheit beide (wegen MAC-Zicken)
                public void keyTyped(KeyEvent e) {
                    checkCanSubmit();
                    checkCanAddTranslation();
                }
                public void keyReleased(KeyEvent e) {
                    checkCanSubmit();
                    checkCanAddTranslation();
                }
            });

            translationTF.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    checkCanAddTranslation();
                }
            });

            translationTF.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    addTranslation();
                }
            });

            addBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    addTranslation();
                }
            });

            cancelBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    hide();
                }
            });

            okBT.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (translationTF.getText().length() > 0) {
                        addTranslation();
                    }
                    submit();
                }
            });

            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
            topPanel.add(Box.createHorizontalGlue());
            topPanel.add(new JLabel("Name"));
            topPanel.add(nameTF);
            topPanel.add(Box.createHorizontalStrut(20));
            topPanel.add(new JLabel("Translation"));
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
                    selectedTranslation = (String)translationLM.get(translationLI.locationToIndex(e
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
            listPanel.add(new JLabel("Translations"), BorderLayout.NORTH);
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
            selectedEntry.getTranslations().add(translationTF.getText());
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

            if (selectedEntry.getID() == null) {
                dictionary.addEntry(selectedEntry);
                refreshDictionary();
            }

            saveAction.setEnabled(true);

            setVisible(false);
        }

        void refreshTranslations() {
            translationLM.removeAllElements();

            if (selectedEntry != null) {
                for (Iterator it = selectedEntry.getTranslations().iterator(); it.hasNext();) {
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

            translationTF.setText("");

            refreshTranslations();

            checkCanSubmit();

            addBT.setEnabled(false);
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
            Collections.sort(sortedKeys, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((String)o1).toLowerCase().compareTo(((String)o2).toLowerCase());
                }
            });

            Vector rows = new Vector();

            for (Iterator it = sortedKeys.iterator(); it.hasNext();) {
                String key = (String)it.next();
                List translations = (List)data.get(key);
                for (int i = 0; i < translations.size(); i++) {
                    String translation = (String)translations.get(i);
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
            labels.add("Word");
            labels.add("Translation");
            model.setDataVector(rows, labels);
        }

    }

    private class TestDialog extends JDialog {
        private JTextField nameTF = new JTextField(20);
        private JTextField inputTF = new JTextField(20);
        private JButton okBT = new JButton("OK");
        private JButton cancelBT = new JButton("cancel");

        public TestDialog(JFrame ownerFrame) {
            super(ownerFrame, "Test", true);
            JPanel basePanel = new JPanel();
            basePanel.setLayout(new BorderLayout());

            nameTF.setEditable(false);

            inputTF.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    okBT.setEnabled(inputTF.getText().length() > 0);
                }
            });

            inputTF.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    submit();
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
                    submit();
                }
            });

            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
            topPanel.add(Box.createHorizontalGlue());
            topPanel.add(new JLabel("Word"));
            topPanel.add(nameTF);
            topPanel.add(Box.createHorizontalStrut(20));
            topPanel.add(new JLabel("Translation"));
            topPanel.add(inputTF);
            topPanel.add(Box.createHorizontalGlue());
            basePanel.add(topPanel, BorderLayout.NORTH);

            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
            bottomPanel.add(Box.createHorizontalGlue());
            bottomPanel.add(okBT);
            bottomPanel.add(cancelBT);

            basePanel.add(bottomPanel, BorderLayout.SOUTH);

            basePanel.setBorder(BorderFactory.createTitledBorder("enter translation"));
            getContentPane().add(basePanel);

            pack();

        }

        void refresh() {
            nameTF.setText(testedEntry.getName());
            inputTF.setText("");
            inputTF.requestFocus();
        }

        void submit() {
            testedEntry.setLastTested(System.currentTimeMillis());
            if (testedEntry.isTranslation(inputTF.getText())) {
                testedEntry.addToDifficulty(-DEC_DIFFICULTY_PER_CORRECT_ANSWER);
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
                setTitle("Test, score:" + numCorrect);
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
    }

    private class FlashLearnerDialog extends JDialog {

        private JTextField wordTF = new JTextField(30);
        private JTextField countTF = new JTextField(10);
        private JTextArea translationsTA = new JTextArea(5, 30);
        private JSlider intervalSlider = new JSlider(100, 2000, flashInterval);
        private Iterator translationIterator = null;
        private ListIterator wordIterator = null;
        private List wordList = null;

        private Timer flashTimer = new Timer(FLASH_INTERVAL, new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                next();
            }
        });

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
            JLabel wordLB = new JLabel("Word");

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

            controlPanel.add(new JButton(new AbstractAction("stop") {
                public void actionPerformed(ActionEvent ae) {
                    stop();
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
            intervalSlider.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent ce) {
                    flashInterval = intervalSlider.getValue();
                    flashTimer.setDelay(flashInterval);
                }
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
            Map reverseMap = dictionary.getReverseMap();
            wordList = new ArrayList(reverseMap.entrySet());
            Collections.shuffle(wordList);
            wordIterator = wordList.listIterator();
        }

        private void stop() {
            flashTimer.stop();
            setVisible(true);
        }

        private void nextEntry() {
            if (!wordIterator.hasNext()) {
                stop();
                return;
            }

            Map.Entry entry = (Entry)wordIterator.next();
            wordTF.setText((String)entry.getKey());
            countTF.setText(wordIterator.previousIndex() + "/" + wordList.size());
            translationIterator = ((List)entry.getValue()).iterator();
            translationsTA.setText("");
        }

        void next() {
            if (translationIterator != null && translationIterator.hasNext()) {
                translationsTA.append((String)translationIterator.next() + "\n");
            } else {
                nextEntry();
            }
        }

    }

    private class PropertyDialog extends JDialog {
        public PropertyDialog() {
            super(mainFrame, "Properties");
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BorderLayout());
            BeanInfo bi = null;
            try {
                bi = Introspector.getBeanInfo(VTrainer.class, Object.class);
            } catch (IntrospectionException e) {
                throw new IllegalStateException("failed to get BeanInfo for VTrainer.class" + e);
            }
            final PropertySheet psheet = new PropertySheet(bi);
            psheet.setObject(VTrainer.this);
            mainPanel.add(psheet, BorderLayout.CENTER);

            JPanel controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
            controlPanel.add(new JButton(new AbstractAction("reset") {
                public void actionPerformed(ActionEvent ae) {
                    psheet.resetIssued(ae);
                }
            }));

            controlPanel.add(new JButton(new AbstractAction("apply") {
                public void actionPerformed(ActionEvent ae) {
                    psheet.applyChangesIssued(ae);
                }
            }));

            controlPanel.add(Box.createHorizontalGlue());

            controlPanel.add(new JButton(new AbstractAction("cancel") {
                public void actionPerformed(ActionEvent ae) {
                    psheet.resetIssued(ae);
                    hide();
                }

            }));
            controlPanel.add(new JButton(new AbstractAction("OK") {
                public void actionPerformed(ActionEvent ae) {
                    psheet.applyChangesIssued(ae);
                    hide();
                }
            }));

            mainPanel.add(controlPanel, BorderLayout.SOUTH);
            getContentPane().add(mainPanel);
            pack();
        }
    }

    public int getFlashInterval() {
        return flashInterval;
    }

    public void setFlashInterval(int flashInterval) {
        this.flashInterval = flashInterval;
    }

}