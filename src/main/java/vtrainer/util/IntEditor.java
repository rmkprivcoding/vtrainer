package vtrainer.util;

import java.awt.Component;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class IntEditor extends PropertyEditorSupport implements PropertyEditor{

    private JSpinner spinner = null;
    private SpinnerNumberModel model = null;
    
    public IntEditor(){
	this(new SpinnerNumberModel());
    }

    public IntEditor(int value, int min, int max, int stepSize){
	this(new SpinnerNumberModel(value, min, max, stepSize));
    }

    private IntEditor(SpinnerNumberModel model){
	this.model = model;
	spinner = new JSpinner(model);
    }
    
    public Component getCustomEditor(){
	return spinner;
    }
    
    public boolean supportsCustomEditor(){
	return true;
    }
    
    public String getAsText(){
	return ""+model.getNumber().intValue();
    }
    
    public void setAsText(String text){
	Integer value = null;
	try{
	    value = Integer.decode(text);
	} catch(NumberFormatException ex){
	    System.err.println("IntEditor.setAsText:" + text + " cannot be parsed as an integer value:" + ex);
	}
	
	model.setValue(value);
    }
    
    public void setValue(Object value) {
	if(!(value instanceof Integer)){
	    throw new IllegalArgumentException("IntEditor:setValue called with non-integer argument:" + value);
	}
	model.setValue(value);
    }
    
    public Object getValue(){
	return model.getValue();
    }
}
