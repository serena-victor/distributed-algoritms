package Project;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;

public class Main {

    public static int N = 3;
    public static int M = 3;


    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );

        ArrayList<ActorRef> references = new ArrayList<>();
        ArrayList<Integer> crash = new ArrayList<>();
        int state;

        Random r = new Random();
        int faultyProcesses = 1; //r.nextInt(N/2);
        int index = 0;

        system.log().info("Number of faulty processes : "+Integer.toString(faultyProcesses));

        for (int i = 0; i < faultyProcesses; i++){
            while (crash.contains(index)){
                index = r.nextInt(N);
            }
            crash.add(index);
        }

        for (int i = 0; i < N; i++) {
            // Instantiate processes*
            if (crash.contains(i)){
                state = 0;
            }
            else {
                state = 1;
            }
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N, M, state), "" + i);
            references.add(a);
        }

        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }
    }
}
