/*
* Server for the cookie service for Networks at Amherst College, Project 3
*
* Written by @author@
*
*/

import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Random;
import java.io.File;


public class CookieServer {
  //===================================================================
  // Instance Variables
  //===================================================================

  //===================================================================
  // Constants
  //===================================================================

  //===================================================================
  // Constructors
  //===================================================================

  //===================================================================
  // Public Methods
  //===================================================================

  public static void showUsageAndExit() {
    System.out.println("invalid arguments : java CookieServer <portNumber>");
    System.exit(1);
  }

  public static void main(String[] args) throws IOException {
    //taking in the arguments from the command line to get port number
    int portNumber = 0;
    if(args.length != 1) {
      showUsageAndExit();
    } else {
      try {
        portNumber = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        showUsageAndExit();
      }
    }

    /* Hashing all of the lines from fortunes.txt so integers, so we
    *  can get a random one when we need to send a fortune later
    */
    HashMap<Integer, String> map = new HashMap<Integer, String>();
    Scanner in = new Scanner(new File("fortunes.txt"));
    int fortunes = 0;
    while(in.hasNextLine()) {
      map.put(fortunes, in.nextLine());
      fortunes++;
    }

    //creating a random object to select a random fortune from a hashmap
    Random r = new Random();


    //actually running the server on the selected port, and sending the message
    ServerSocket server = new ServerSocket(portNumber);
    System.out.println("Listnening on port " + portNumber + "...");
    try {
        Socket socket = server.accept();
        System.out.println("Connection Established");

        //the message to be sent will be a random line from the text of fortunes
        socket.getOutputStream().write(map.get(r.nextInt(map.size() - 1)).getBytes("UTF-8"));
        System.out.println("Message sent");
        System.out.println("Exiting");
        server.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
  }

  //===================================================================
  // Private Methods
  //===================================================================

}
