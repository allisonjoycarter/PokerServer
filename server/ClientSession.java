package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.lang.*;
import javax.swing.*;

import game.Card;

public class ClientSession {
    private int port;
    private int mutablePort;
    private String address;
    private Socket centralSocket;
    private Socket controlSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private Server server;


    public ClientSession(String username, String centralAddress) throws UnknownHostException {
        try {
            //connects to central server
            this.centralSocket = new Socket(centralAddress, 1200);
            this.address = InetAddress.getLoopbackAddress().getHostAddress();
            this.port = this.centralSocket.getLocalPort();
            this.mutablePort = this.port + 7;
            String connMessage = String.format("newuser: %s %s:%d \n", username, this.address, this.port);
            System.out.println("Registering with central: " + connMessage);
            DataOutputStream centralDOS = new DataOutputStream(this.centralSocket.getOutputStream());
            centralDOS.writeBytes(connMessage);
            new Thread(
                this.server = new Server(this.port, username)
            ).start();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (Exception e) {
            if (e instanceof UnknownHostException) {
                throw new UnknownHostException(e.getMessage());
            }
            e.printStackTrace();
            System.out.println("Wow that sucked");
        }
    }


    public void startGame() {
        try {
            DataOutputStream centralDOS = new DataOutputStream(this.centralSocket.getOutputStream());
            centralDOS.writeBytes(String.format("start %s:%d\n", this.address, this.port));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PlayersObservable getObservable() {
        return this.server.getObservable();
    }

    /**
     * Send message to all other players
     * @param message message to send
     */
    public void broadcast(String message) {
        for (Player player: this.getObservable().getPlayers()) {
            if (player.getHostName().contains(this.getHostName())) { continue; } // don't send to ourselves
            String[] serverInfo = player.getHostName().split(":");
            try {
                Socket tmpSocket = new Socket(serverInfo[0], Integer.parseInt(serverInfo[1]));
                DataOutputStream dos = new DataOutputStream(tmpSocket.getOutputStream());
                dos.writeBytes(message);
                dos.flush();
                dos.close();
                tmpSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendCheckMessage() {
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d action: %s Check\n", this.mutablePort, this.getHostName());
        broadcast(message);
        this.getObservable().setLastAction(this.getHostName(), "Check");
    }

    public void sendBetMessage(int amount) {
        int myIndex = 0;
        for (int i = 0; i < this.getObservable().getPlayers().size(); i++) {
            if (this.getObservable().getPlayers().get(i).getHostName().equals(this.getHostName())) {
                myIndex = i;
                break;
            }
        }
        // give money to the pot
        this.getObservable().getPlayers().get(myIndex).removeMoney(amount);
        this.getObservable().addToPot(amount);
        
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d action: %s Bet %d\n", this.mutablePort, this.getHostName(), amount);
        if (this.getObservable().getLastAction().getValue().startsWith("Bet")) {
            this.getObservable().setLastAction(this.getHostName(), "Call $" + amount);
        } else {
            this.getObservable().setLastAction(this.getHostName(), "Bet $" + amount);
            this.getObservable().setLastPlayerToBet(this.getHostName());
        }
        broadcast(message);
    }

    public void sendDeckMessage(Card card1, Card card2, String hostName){
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d deck: %s Deck %d %d %d %d %s \n", 
            this.mutablePort,
            this.getHostName(),
            card1.rank(),
            card1.suit(),
            card2.rank(),
            card2.suit(),
            hostName);
        System.out.println(message);
        broadcastDeck(message);
    }

    public void broadcastDeck(String message){
        String[] commands;
        commands = message.split(" ");
        for (Player player: this.getObservable().getPlayers()) {
            if (player.getHostName().contains(commands[2])) { continue; }
            if(player.getHostName().contains(commands[8])){
                String[] serverInfo = player.getHostName().split(":");
            try {
                Socket tmpSocket = new Socket(serverInfo[0], Integer.parseInt(serverInfo[1]));
                DataOutputStream dos = new DataOutputStream(tmpSocket.getOutputStream());
                dos.writeBytes(message);
                dos.flush();
                dos.close();
                tmpSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        }
    }
    public void sendFoldMessage() {
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d action: %s Fold\n", this.mutablePort, this.getHostName());
        this.getObservable().setLastAction(this.getHostName(), "Fold");
        broadcast(message);
        
        int myIndex = 0;
        for (int i = 0; i < this.getObservable().getPlayers().size(); i++) {
            if (this.getObservable().getPlayers().get(i).getHostName().equals(this.getHostName())) {
                myIndex = i;
                break;
            }
        }
        //check if its a fold win.
        this.getObservable().getPlayers().get(myIndex).fold();
        int j =1;
        String host="";
        for (int i = 0; i < this.getObservable().getPlayers().size(); i++) {
            if (this.getObservable().getPlayers().get(i).hasFolded()) {
                j++;
            }else{
                host = this.getObservable().getPlayers().get(i).getHostName();
            }
        }
        if(j==this.getObservable().getPlayers().size()){
           // this.mutablePort += 7;
           // message = String.format("%d winner: %s\n", this.mutablePort, host);
            //this.getObservable().setRoundWinner(host);
            //broadcast(message);
            try {
                DataOutputStream centralDOS = new DataOutputStream(this.centralSocket.getOutputStream());
                centralDOS.writeBytes(String.format("foldWin: %s\n", host));
                centralDOS.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("There was a problem sending your score.");
            }
        }

    }

    public void endPhase() {
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d endPhase %s\n",this.mutablePort, this.getHostName());
        broadcast(message);

    }

    public void endRound() {
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String message = String.format("%d endRound %s\n",this.mutablePort, this.getHostName());
        broadcast(message);
    }

    public void sendNextPhase(Card[] cards) {
        this.mutablePort += 7;
        String gameHost = this.server.getObservable().getHost();
        String cardString = "";
        ArrayList<Card> cardArray = new ArrayList<>();
        for (Card card: cards) {
            cardArray.add(card);
            cardString += " " + card.rank() + " " + card.suit();
        }
        System.out.println("ending phase from cs");
       //String message = String.format("%d endPhase %s\n",this.mutablePort, this.getHostName());
        String message = String.format("%d endPhase %s %s\n", this.mutablePort, this.getHostName(), cardString);
        broadcast(message);
        this.getObservable().addToBoard(cardArray);
        //endPhase();
    }

    public void sendScore(int score) {
        try {
            DataOutputStream centralDOS = new DataOutputStream(this.centralSocket.getOutputStream());
            centralDOS.writeBytes(String.format("score: %s %d\n", this.getHostName(), score));
            centralDOS.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("There was a problem sending your score.");
        }
    }

    public void sendCards(Card[] cards) {
        this.mutablePort += 7;
        String message = String.format("%d cards: %s %s %s %s %s\n", 
            this.mutablePort, this.getHostName(), cards[0].suit(), cards[0].rank(), cards[1].suit(), cards[1].rank());
        broadcast(message);
    }

    //closes client session
    public void close() {
        if (this.controlSocket != null) {
            try {
                this.mutablePort += 7;
                String closeCommand = this.port + " close \n";
                this.out.writeBytes(closeCommand);
                this.controlSocket.close();
                this.controlSocket = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } try {
            DataOutputStream centralDOS = new DataOutputStream(this.centralSocket.getOutputStream());
            centralDOS.writeBytes("close " + this.address + ":" + this.port + "\n");
        } catch(IOException d){
            d.printStackTrace();
        }
    }

    public boolean isConnected() {
        return this.controlSocket != null;
    }

    public Socket getSocket() {
        return this.controlSocket;
    }

    public DataOutputStream getOutputStream() {
        return this.out;
    }

    public DataInputStream getInputStream() {
        return this.in;
    }

    public String getHostName() {
        return String.format("%s:%d", this.address, this.port);
    }
}
