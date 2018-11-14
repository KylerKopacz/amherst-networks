/*
* Client for the cookie service for Networks at Amherst College, Project 3
*
* Written by @author@
*
*/

import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Scanner;
import java.net.ConnectException;


public class CookieClient {
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
    System.out.println("invalid arguments : java CookieClient <ip address> <port number>");
    System.exit(1);
  }

  public static void main(String[] args) throws IOException {
    String address = "";
    int portNumber = 0;
    if(args.length != 2) {
      showUsageAndExit();
    } else {
      address = args[0];
      try {
        portNumber = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        showUsageAndExit();
      }
    }

    try {
      Socket socket = new Socket(address, portNumber);
      System.out.println("Connecting to " + address + ':' + portNumber);
      System.out.println("Connection Established");
      Scanner s = new Scanner(socket.getInputStream()).useDelimiter("\\A");
      String result = s.hasNext() ? s.next() : "";
      System.out.println("Your fortune: " + result);
      socket.close();
    } catch (ConnectException e) {
      System.out.println("Connection error. Check address and port number.");
    }
  }

  //===================================================================
  // Private Methods
  //===================================================================

}
