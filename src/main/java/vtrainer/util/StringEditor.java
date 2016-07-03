package vtrainer.util;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

public class StringEditor extends PropertyEditorSupport implements PropertyEditor{
    
    private JTextComponent textComponent;
    public static final int DEFAULT_NUM_COLS = 20;
    private boolean treatEmptyAsNull = true;
    
    public StringEditor(int numCols, int numRows){
	if(numRows < 1){
	    throw new IllegalArgumentException("StringEditor:numRows < 1 (" + numRows + ")");
	}
	
	if(numRows > 1){
	    JTextArea ta = new JTextArea(numRows, numCols);
	    ta.setLineWrap(true);
	    ta.setWrapStyleWord(true);
	    textComponent = ta;
	} else{
	    textComponent = new JTextField(numCols);
	    ((JTextField)textComponent).addActionListener(new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			setAsText(textComponent.getText());
		    }
		});
	}
	
	textComponent.addFocusListener(new FocusAdapter(){
		public void focusLost(FocusEvent e){
		    setAsText(textComponent.getText());
		}
	    });
    }
    
    public void setTreatEmptyAsNull(boolean treatEmptyAsNull){
	this.treatEmptyAsNull = treatEmptyAsNull;
    }
    
    public void setAsText(String text) {
	setValue(text);
    }
    
    public StringEditor(int numCols){
	this(numCols, 1);
    }
    
    public StringEditor(){
	this(DEFAULT_NUM_COLS);
    }
    
    public Component getCustomEditor(){
	if(textComponent instanceof JTextArea){
	    return new JScrollPane(textComponent);
	}
	
	return textComponent;
    }

    public boolean supportsCustomEditor(){
	return true;
    }
    
    public void setValue(Object value) {
	if(value != null && !(value instanceof String)){
	    throw new IllegalArgumentException("StringEditor:setValue called with non-string argument:" + value);
	}
	super.setValue(value);
	textComponent.setText((String)value);
    }
}

