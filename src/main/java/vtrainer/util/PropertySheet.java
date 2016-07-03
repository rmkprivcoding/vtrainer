package vtrainer.util;

import java.awt.event.ActionEvent;
import java.beans.BeanInfo;
import java.beans.Customizer;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PropertySheet extends JPanel implements DeferredChangeListener, Customizer {
    
    private Object object = null;
    private BeanInfo beanInfo = null;
    private Map propertyInfos = new LinkedHashMap();
    
    public PropertySheet(BeanInfo beanInfo){
	this.beanInfo = beanInfo;
	JPanel dummyPanel = new JPanel();
	dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.Y_AXIS));
	
	List descriptors = BeansUtil.reorder(beanInfo.getPropertyDescriptors());
	for(Iterator iter = descriptors.iterator();iter.hasNext();){
	    PropertyDescriptor desc = (PropertyDescriptor)iter.next();
	    PropertyEditor editor = null;
	    
	    // only works with read/write-properties
	    if(desc.getReadMethod() != null && desc.getWriteMethod() != null){
		if(desc.getPropertyType().equals(String.class)){
		    if(desc.getValue("tags") != null){
			Set tags = (Set)desc.getValue("tags"); 
			editor = new TaggedStringEditor(tags);
		    } else{
			int cols = StringEditor.DEFAULT_NUM_COLS;
			Integer preferredCols = (Integer)desc.getValue("preferredCols");
			if(preferredCols != null){
			    cols = preferredCols.intValue();
			}
			
			int rows = 1;
			Integer preferredRows = (Integer)desc.getValue("preferredRows");
			
			if(preferredRows != null){
			    rows = preferredRows.intValue();
			}
			
			editor = new StringEditor(cols, rows);
		    } 
		}else if(desc.getPropertyType().equals(File.class)){
		    editor = new FileEditor();
		    if(desc.getValue("fileSelectionMode") != null){
			int mode = ((Integer)desc.getValue("fileSelectionMode")).intValue();
			((FileEditor)editor).setFileSelectionMode(mode);
		    }
		}else{
		    editor = PropertyEditorManager.findEditor(desc.getPropertyType());
		}
	    }
	    
	    if(editor != null && editor.supportsCustomEditor()){
		propertyInfos.put(desc.getName(), new PropertyInfo(desc, editor));
		String displayName = desc.getDisplayName();
		JPanel editorPanel = new JPanel();
		editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));
		editorPanel.add(new JLabel(displayName));
		editorPanel.add(Box.createHorizontalGlue());
		editorPanel.add(editor.getCustomEditor());
		dummyPanel.add(editorPanel);
	    }
	}
	
	add(dummyPanel);
    }
    
    public void setObject(Object bean){
	if(bean != null && !(beanInfo.getBeanDescriptor().getBeanClass().isAssignableFrom(bean.getClass()))){
	    throw new IllegalArgumentException("setObject called with incompatible bean " + bean);
	}
	object = bean;
	reset();
    }
    
    
    private void reset(){
	
	for(Iterator iter = propertyInfos.values().iterator();iter.hasNext();){
	    PropertyInfo pi = (PropertyInfo)iter.next();
	    Object value = null;
	    try{
		value = pi.getDescriptor().getReadMethod().invoke(object, new Object[0]);
	    } catch(InvocationTargetException ex){
		throw new IllegalStateException("invoking read method for property " + pi.getDescriptor().getName() + " failed");
	    } catch(IllegalAccessException ex){
		throw new IllegalStateException("invoking read method for property " + pi.getDescriptor().getName() + " failed");
	    }
	    pi.getEditor().setValue(value);
	}
    }
    
    public void applyChangesIssued(ActionEvent e){
	
	if(object == null){
	    System.err.println("applyChangesIssued() called with null object:" + beanInfo.getBeanDescriptor().getDisplayName());
	    return;
	}
	
	for(Iterator iter = propertyInfos.values().iterator();iter.hasNext();){
	    PropertyInfo pi = (PropertyInfo)iter.next();
	    Object value = null;
	    
	    try{
		Object newValue = pi.getEditor().getValue();
		Object[] setterArgs = {newValue};
		pi.getDescriptor().getWriteMethod().invoke(object, setterArgs);
	    } catch(InvocationTargetException ex){
		// XXX besser behandeln
		throw new IllegalStateException(ex.getCause().getMessage());
	    } catch(IllegalAccessException ex){
		throw new IllegalStateException("invoking write method for property " + pi.getDescriptor().getName() + " failed");
	    }
	    
	}
    }
    
    public void resetIssued(ActionEvent e){
	if(object == null){
	    System.err.println("resetIssued() called with null object:" + beanInfo.getBeanDescriptor().getDisplayName());
	    return;
	}
	
	reset();
    }
    
    private class PropertyInfo{
	
	private PropertyEditor editor = null;
	private PropertyDescriptor descriptor = null;
	
	public PropertyInfo(
			    PropertyDescriptor descriptor,
			    PropertyEditor editor 
			    ){
	    this.descriptor = descriptor;
	    this.editor = editor;
	}
	
	public PropertyEditor getEditor(){
	    return editor;
	}
	
	public PropertyDescriptor getDescriptor(){
	    return descriptor;
	}	
    }
}
