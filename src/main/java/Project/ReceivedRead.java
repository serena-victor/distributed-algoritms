package Project;

import java.util.HashMap;

public class ReceivedRead {
    
    public HashMap<String, Integer> readAnswer;
    public boolean putAfter;

    public ReceivedRead(HashMap<String, Integer> readAnswer, boolean putAfter){
        this.readAnswer = readAnswer;
        this.putAfter = putAfter;
    }
}
