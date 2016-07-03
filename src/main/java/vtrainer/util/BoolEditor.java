package vtrainer.util;

import java.awt.Component;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;

import javax.swing.JCheckBox;

public class BoolEditor extends PropertyEditorSupport implements PropertyEditor{
    private JCheckBox checkBox = new JCheckBox();
    
    public Component getCustomEditor(){
	return checkBox;
    }
    
    public boolean supportsCustomEditor(){
	return true;
    }
    
    public String getAsText(){
	return "" + checkBox.isSelected();
    }
    
    public void setAsText(String text){
	checkBox.setSelected("true".equalsIgnoreCase(text));
    }
    
    public void setValue(Object value) {
	if(!(value instanceof Boolean)){
	    throw new IllegalArgumentException("BoolEditor:setValue called with non-boolean argument:" + value);
	}
	checkBox.setSelected(((Boolean)value).booleanValue());
    }
    
    public Object getValue(){
	return new Boolean(checkBox.isSelected());
    }
}
