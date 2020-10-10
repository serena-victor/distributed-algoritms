package Project;

import java.util.HashMap;

public class ReceivedRead {
    
    public HashMap<String, Integer> readAnswer;
    public boolean putAfter;
    public int currentRead;

    public ReceivedRead(HashMap<String, Integer> readAnswer, boolean putAfter, int currentRead){
        this.readAnswer = readAnswer;
        this.putAfter = putAfter;
        this.currentRead = currentRead;
    }
}
