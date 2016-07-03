package vtrainer.util;

import java.awt.Component;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JComboBox;

public class TaggedStringEditor extends PropertyEditorSupport implements PropertyEditor{
    
    private JComboBox combo;
    private Set tags = null;
    
    public TaggedStringEditor(Set tags){
	this.tags = new LinkedHashSet(tags);
	combo = new JComboBox(tags.toArray());
    }
    
    public void setAsText(String text) {
	setValue(text);
    }
    
    public Component getCustomEditor(){
	return combo;
    }
    
    public boolean supportsCustomEditor(){
	return true;
    }
    
    public Object getValue() {
	return combo.getSelectedItem();
    }
    
    public String[] getTags(){
	return (String[])tags.toArray(new String[0]);
    }
    
    public void setValue(Object value) {
	if(value != null && !(value instanceof String)){
	    throw new IllegalArgumentException("TaggedStringEditor:setValue called with non-string argument:" + value);
	}
	if(!tags.contains(value)){
	    throw new IllegalArgumentException("argument " + value + " is not among allowed tags " + tags);
	}
	combo.setSelectedItem(value);
    }
}

