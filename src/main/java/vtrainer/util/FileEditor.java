package vtrainer.util;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyEditorSupport;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class FileEditor extends PropertyEditorSupport{
    
    private SimpleFileChooser chooser = new SimpleFileChooser();

    public Component getCustomEditor(){
	return chooser;
    }
    
    public boolean supportsCustomEditor(){
	return true;
    }
    
    public String getAsText(){
	File file = (File)getValue();
	if(file != null){
	    return file.getAbsolutePath();
	}
	return null;
    }
    
    public void setAsText(String text){
	setValue(new File(text));
    }

    public void setValue(Object value){
	if(value != null && !(value instanceof File)){
	    throw new IllegalArgumentException("setValue called with non-file argument:" + value);
	}
	super.setValue(value);
	chooser.setFile((File)value);
    }
    
    public void setFileSelectionMode(int mode){
	chooser.setFileSelectionMode(mode);
    }

    class SimpleFileChooser extends JPanel{
	
	private int mode = JFileChooser.FILES_AND_DIRECTORIES;
	private JTextField textField = new JTextField("", 20);
	
	SimpleFileChooser(){
	    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));	
	    textField.setText(getAsText());
	    textField.addFocusListener(new FocusAdapter(){
		    public void focusLost(FocusEvent e){
			setAsText(textField.getText());
		    }
		});
	    textField.setText(getAsText());
	    textField.addActionListener(new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			setAsText(textField.getText());
		    }
		});
	    add(textField);
	    JButton button = new JButton("...");
	    add(button);
	    button.addActionListener(new ActionListener(){
		    public void actionPerformed(ActionEvent ae){
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(mode);
			chooser.setCurrentDirectory((File)getValue());
			int option = chooser.showOpenDialog(null);
			if(option == JFileChooser.APPROVE_OPTION){
			    File file = chooser.getSelectedFile();
			    if(file != null){
				textField.setText(file.getAbsolutePath());
				setValue(file.getAbsoluteFile());
			    }
			}
		    }
		});
	}

	public void setFile(File file){
	    if(file != null)
		textField.setText(file.getAbsolutePath());
	}

	public void setFileSelectionMode(int mode){
	    this.mode = mode;
	}
    }
}
