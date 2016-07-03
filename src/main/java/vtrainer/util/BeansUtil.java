package vtrainer.util;

import java.beans.FeatureDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BeansUtil{
    
    public static void setFeatureDescriptorOrder(List descriptors){
	
	for(int i=0;i<descriptors.size();i++){
	    FeatureDescriptor desc = (FeatureDescriptor)descriptors.get(i);
	    desc.setValue("displayOrder", new Integer(i));
	}
	
    }
    
    public static List reorder(FeatureDescriptor[] descriptors){
	List list = Arrays.asList(descriptors);
	Collections.sort(list, new FeatureDescriptorComparator()); 
	return list;
    }
    
    public static class FeatureDescriptorComparator implements Comparator{
	public int compare(Object o1, Object o2){
	    if(
	       !(o1 instanceof FeatureDescriptor) || 
	       !(o2 instanceof FeatureDescriptor)
	       ){
		throw new IllegalArgumentException("FeatureDescriptorComparator.compare called with wrong type:" + o1 + "," + o2);
	    }
	    
	    Integer int1 = (Integer)((FeatureDescriptor)o1).getValue("displayOrder");
	    if(int1 == null){
		int1 = new Integer(0);
	    }
	    
	    Integer int2 = (Integer)((FeatureDescriptor)o2).getValue("displayOrder");
	    if(int2 == null){
		int2 = new Integer(0);
	    }
	    
	    return int1.compareTo(int2);
	}
    }  
    
}
