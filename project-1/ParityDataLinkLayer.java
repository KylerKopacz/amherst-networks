// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
// =============================================================================


// =============================================================================
/**
* @file   ParityDataLinkLayer.java
* @author Kyler Kopacz (kkopacz21@amherst.edu)
* @date   September 2018
*
* A data link layer that uses start/stop tags and byte packing to frame the
* data, and that performs no error management. Error checking using a parity bit
*/
public class ParityDataLinkLayer extends DataLinkLayer {
  // =============================================================================



  // =========================================================================
  /**
  * Embed a raw sequence of bytes into a framed sequence.
  *
  * @param  data The raw sequence of bytes to be framed.
  * @return A complete frame.
  */
  protected byte[] createFrame (byte[] data) {

    Queue<Byte> framingData = new LinkedList<Byte>();

    // Begin with the start tag.
    framingData.add(startTag);

    //get the parity of the bytes that have been recieved
    byte parity = getParity(data);
    //then add it to the list
    framingData.add(parity);

    // Add each byte of original data.
    for (int i = 0; i < data.length; i += 1) {

      // If the current data byte is itself a metadata tag, then precede
      // it with an escape tag.
      byte currentByte = data[i];
      if ((currentByte == startTag) ||
      (currentByte == stopTag) ||
      (currentByte == escapeTag)) {

        framingData.add(escapeTag);

      }

      // Add the data byte itself.
      framingData.add(currentByte);

    }

    // End with a stop tag.
    framingData.add(stopTag);

    // Convert to the desired byte array.
    byte[] framedData = new byte[framingData.size()];
    Iterator<Byte>  i = framingData.iterator();
    int             j = 0;
    while (i.hasNext()) {
      framedData[j++] = i.next();
    }

    return framedData;

  } // createFrame ()
  // =========================================================================
  /**
  * Given a message, split the data into frames and return the new byte[] with the
  * framed data
  *
  * @param data The data to be split into frames
  */
  public byte[] frameData(byte[] data) {
    //take the total length of the data and find how many full frames we can make
    int numFrames = data.length / 8;
    int remainder = data.length % 8;

    //an arraylist to hold all of the frames that have been processed
    ArrayList<Byte> processedChunks = new ArrayList<Byte>();

    //so we should split the data into 8 byte chunks and send them off to be framed
    for(int i = 0; i < numFrames + 1; i++) {
      if(i < numFrames) {
        byte[] toFrame = new byte[8];

        //this is the loop to add the byte to an array and send them off to be frames
        for(int j = 0; j < 8; j++) {
          toFrame[j] = data[(i*8) + j];
        }

        //now send that created array to become a frame
        byte[] framedPortion = createFrame(toFrame);

        //add all of the bytes from the framed chunk to the rest of the message
        for(byte b: framedPortion) {
          processedChunks.add(b);
        }
      } else {//this must be the remainder frame
        byte[] toFrame = new byte[remainder];

        //this is the loop to add the byte to an array and send them off to be frames
        for(int j = 0; j < remainder; j++) {
          toFrame[j] = data[(i*8) + j];
        }

        //now send that created array to become a frame
        byte[] framedPortion = createFrame(toFrame);

        //add all of the bytes from the framed chunk to the rest of the message
        for(byte b: framedPortion) {
          processedChunks.add(b);
        }
      }
    }

    //now that all of the data has been processed, we need to convert the
    //arraylist back into a byte array
    byte[] dataFramed = new byte[processedChunks.size()];
    Iterator<Byte> i = processedChunks.iterator();
    int j = 0;
    while (i.hasNext()) {
      dataFramed[j++] = i.next();
    }

    //now return the array with all of the framed data
    return dataFramed;
  } //frameData()
  // =========================================================================
  /* Gets the parity of the byte array passed in
  *
  * @param data The Array of which we are calculating the parity of
  */
  public byte getParity(byte[] data) {
    byte sumOfOnes = 0;

    //loop through all of the bytes and get the total number of ones
    for(byte b: data) {
      //check all of the bits of the current byte
      for(int i = 0; i < 8; i++) {
        byte myBit = (byte)((b >>> i) & 0x1);
        if(myBit == 1) {
          sumOfOnes++;
        }
      }
    }

    //now that we have the total number of ones, we can mod the sum
    //to see if we have an even or odd parity
    byte parity = (byte)(sumOfOnes % 2);
    return parity;
  } //getParity()
  // =========================================================================
  /**
  * Send a sequence of bytes through the physical layer.  Expected to be
  * called by the client.
  *
  * @param data The sequence of bytes to send.
  */
  public void send (byte[] data) {

    // Call on the underlying physical layer to send the data.
    byte[] framedData = frameData(data);
    for (int i = 0; i < framedData.length; i += 1) {
      transmit(framedData[i]);
    }

  } //send()
  // =========================================================================
  /**
  * Determine whether the received, buffered data constitutes a complete
  * frame.  If so, then remove the framing metadata and return the original
  * data.  Note that any data preceding an escaped start tag is assumed to be
  * part of a damaged frame, and is thus discarded.
  *
  * @return If the buffer contains a complete frame, the extracted, original
  * data; <code>null</code> otherwise.
  */
  protected byte[] processFrame() {
    //parity byte
    byte parity = -1;

    // Search for a start tag.  Discard anything prior to it.
    boolean startTagFound = false;
    Iterator<Byte> i = byteBuffer.iterator();
    while (!startTagFound && i.hasNext()) {
      byte current = i.next();
      if (current != startTag) {
        i.remove();
      } else {
        startTagFound = true;
        //the byte directly after this has to be the partiy bit
        if(i.hasNext()) {
          parity = i.next();
        }
      }
    }

    // If there is no start tag, then there is no frame.
    if (!startTagFound) {
      return null;
    }


    // Try to extract data while waiting for an unescaped stop tag.
    Queue<Byte> extractedBytes = new LinkedList<Byte>();
    boolean stopTagFound = false;
    while (!stopTagFound && i.hasNext()) {

      // Grab the next byte.  If it is...
      //   (a) An escape tag: Skip over it and grab what follows as
      //                      literal data.
      //   (b) A stop tag:    Remove all processed bytes from the buffer and
      //                      end extraction.
      //   (c) A start tag:   All that precedes is damaged, so remove it
      //                      from the buffer and restart extraction.
      //   (d) Otherwise:     Take it as literal data.
      byte current = i.next();
      if (current == escapeTag) {
        if (i.hasNext()) {
          current = i.next();
          extractedBytes.add(current);
        } else {
          // An escape was the last byte available, so this is not a
          // complete frame.
          return null;
        }
      } else if (current == stopTag) {
        cleanBufferUpTo(i);
        stopTagFound = true;
      } else if (current == startTag) {
        cleanBufferUpTo(i);
        extractedBytes = new LinkedList<Byte>();
      } else {
        extractedBytes.add(current);
      }

    }

    // If there is no stop tag, then the frame is incomplete.
    if (!stopTagFound) {
      return null;
    }

    // Convert to the desired byte array.
    if (debug) {
      System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
    }
    byte[] extractedData = new byte[extractedBytes.size()];
    int j = 0;
    i = extractedBytes.iterator();
    while (i.hasNext()) {
      extractedData[j] = i.next();
      if (debug) {
        System.out.printf("ParityDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
        j,
        extractedData[j]);
      }
      j += 1;
    }

    //now we have to see if the calculated parity is the same as what we recieved
    byte calculatedParity = getParity(extractedData);
    if(calculatedParity == parity) {
      return extractedData;
    } else {
      //print out the corrupted data message and return that
      System.out.print("Parity Error! (data:");
      for(byte b: extractedData) {
        System.out.print(b + " ");
      }
      System.out.println(") (Parity Recieved: " + parity +
        " Parity calculated: " + calculatedParity);
      return null;
    }
  } // processFrame ()
  // ===============================================================



  // ===============================================================
  private void cleanBufferUpTo (Iterator<Byte> end) {

    Iterator<Byte> i = byteBuffer.iterator();
    while (i.hasNext() && i != end) {
      i.next();
      i.remove();
    }

  }
  // ===============================================================



  // ===============================================================
  // DATA MEMBERS
  // ===============================================================



  // ===============================================================
  // The start tag, stop tag, and the escape tag.
  private final byte startTag  = (byte)'{';
  private final byte stopTag   = (byte)'}';
  private final byte escapeTag = (byte)'\\';
  // ===============================================================



  // ===================================================================
} // class DumbDataLinkLayer
// ===================================================================
