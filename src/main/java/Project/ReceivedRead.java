package Project;

import java.util.HashMap;

public class ReceivedRead {
    
    public HashMap<String, Integer> readAnswer;
    public boolean overrideValue;

    public ReceivedRead(HashMap<String, Integer> readAnswer, boolean overrideValue){
        this.readAnswer = readAnswer;
        this.overrideValue = overrideValue;
    }
}
