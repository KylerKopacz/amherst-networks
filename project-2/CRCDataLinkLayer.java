// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
// =============================================================================


// =============================================================================
/**
* @file   CRCDataLinkLayer.java
* @author Kyler Kopacz (kkopacz21@amherst.edu)
* @date   September 2018
*
* A data link layer that uses start/stop tags and byte packing to frame the
* data, and checks to see if there were any errors using CRC
*/
public class CRCDataLinkLayer extends DataLinkLayer {
  // =============================================================================



  // =========================================================================
  /**
  * Embed a raw sequence of bytes into a framed sequence.
  *
  * @param  data The raw sequence of bytes to be framed.
  * @return A complete frame.
  */
  public byte[] createFrame (byte[] data) {

    Queue<Byte> framingData = new LinkedList<Byte>();

    // Begin with the start tag.
    framingData.add(startTag);

    //add the ACK/NAK or data frame byte
    //this will be a 0 because it's not an ACK/NAK and there's no status for it
    framingData.add((byte) 0);

    //we are going to do frame 0 and frame 1, for simplicity's sake
    framingData.add(sentFrameNumber);

    //now we add the CRC of the data
    framingData.add(getCRC8(data));

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
  public static byte getCRC8(byte[] data) {
    //create a long with all bits set to 0
    long dividend = 0L;

    //generator function for CRC8
    int generator = 0b0000000111010101;

    //now that we have have all the bytes, we need to append the bits
    //of each to the long
    for(byte b: data) {
      //create a for loop for all 8 bits in the byte
      for(int i = 7; i >= 0; i--) { //start from the left-most bit
        //get the bit from the byte
        byte myBit = (byte)((b >>> i) & 1);
        // System.out.println("Bit to be added: " + myBit);

        //shift the long by 1, and OR the bit above into the last place
        dividend = (dividend << 1) | myBit;
        // System.out.println("New Dividend: " + byteToBinary((byte) dividend));

        //if we can XOR the bits, then do it
        if(((dividend >>> 8) & 1) == 1) {
          dividend = (dividend ^ generator);
          // System.out.println("XOR!!! New Dividend: " + byteToBinary((byte) dividend));
        }
      }
    }
    //we still need to append 6 zeros and xor
    for(int i = 0; i < 8; i++) {
      dividend = (dividend << 1) | 0;
      // System.out.println("New Dividend: " + byteToBinary((byte) dividend));

      //if we can XOR the bits, then do it
      if(((dividend >>> 8) & 1) == 1) {
        dividend = (dividend ^ generator);
        // System.out.println("XOR!!! New Dividend: " + byteToBinary((byte) dividend));
      }
    }

    //now all that remains should be the remainder of the long division
    return (byte) dividend;
  } //getCRC8()
  // =========================================================================

  /**
  * Send a sequence of bytes through the physical layer.  Expected to be
  * called by the client.
  *
  * @param data The sequence of bytes to send.
  */
  public void send (byte[] data) {

    // Buffer the data to send.
    bufferForSending(data);

    // Send until the buffer is empty.
    while (sendBuffer.peek() != null) {
      sendNextFrame();
    }

  }
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
  protected Queue<Byte> processFrame() {

    // Search for a start tag.  Discard anything prior to it.
    boolean startTagFound = false;
    Iterator<Byte> i = receiveBuffer.iterator();
    while (!startTagFound && i.hasNext()) {
      byte current = i.next();
      if (current != startTag) {
        i.remove();
      } else {
        startTagFound = true;
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

    //lets see what is in the queue
    Iterator<Byte> j = extractedBytes.iterator();
    //System.out.println("DEBUG");
    // while(j.hasNext()) {
    //   System.out.print(j.next().byteValue() + " ");
    // }
    //System.out.println();

    return extractedBytes;
  } // processFrame ()
  // ===============================================================




  // ===============================================================
  private void cleanBufferUpTo (Iterator<Byte> end) {

    Iterator<Byte> i = receiveBuffer.iterator();
    while (i.hasNext() && i != end) {
      i.next();
      i.remove();
    }

  }
  // ===============================================================
  /**
  * An entire frame has been received.  Complete its processing, which may
  * involve checking its correctness, responding to the sender, and/or
  * delivering the frame to the client (if correct).  Called by
  * <code>receive()</code>.
  *
  * @param data The de-tagged contents extracted from the frame.
  * @see   DataLinkLayer.receive
  */
  protected void finishFrameReceive (Queue<Byte> data) {
    //we need to extract the metadata out of the frame
    Iterator<Byte> i = data.iterator();

    //first is the whether or not this is an ACK/NAK frame
    if(i.hasNext()) {
      byte ackStatus = i.next();
      if(ackStatus >> 4 == 1) {//we do nothing, because finishFrameSend will be called to parse response
        //then we just get out of here
        ackStuff = data;
        return;
      }
    }

    //next is the frame number
    byte frameNumber = -1;
    if(i.hasNext()) {
      frameNumber = i.next();
    }

    //next we do the CRC checksum
    byte crc = 0;
    if(i.hasNext()) {
      crc = i.next();
    }


    //the rest of the frame is the data
    byte[] extractedData = new byte[data.size() - 3];
    for(int j = 0; j < extractedData.length && i.hasNext(); j++) {
      extractedData[j] = i.next();
    }

    //send this data to get CRC
    byte messageCRC = getCRC8(extractedData);

    //if this doesn't match, then send NAK frame
    if(messageCRC != crc) {
      //System.out.println("CRC doesn't match, sending NAK frame");
      sendNAKFrame(receivedFrameNumber);
      return;
    } else if(frameNumber != receivedFrameNumber) {
      //the only way that these would be out of sync is
      //if the ACK frame didn't go through correctly.
      //so send an ACK Frame with the other confirmation number
      if(receivedFrameNumber == 0) {
        sendACKFrame((byte) 1);
      } else {
        sendACKFrame((byte) 0);
      }
      return;
    } else {//the data is legit, pass it to the host
      client.receive(extractedData);
      //System.out.println("Sending ACK FRAME! NOICE");
      sendACKFrame(receivedFrameNumber);
      if(receivedFrameNumber == 0) {
        receivedFrameNumber = 1;
        //System.out.println("Setting receivedFrameNumber to " + receivedFrameNumber);
      } else {
        receivedFrameNumber = 0;
        //System.out.println("Setting receivedFrameNumber to " + receivedFrameNumber);
      }
    }
  } //finishFrameReceive()
  // ===============================================================

  /* Sends a NAK frame to the host, requiring it to send the frame again
  */
  protected void sendNAKFrame(byte num) {
    byte[] nakFrame = new byte[5];

    //start tag
    nakFrame[0] = startTag;
    //NAK byte = 0b00010000
    nakFrame[1] = (byte) 16;
    //frame number of error
    nakFrame[2] = num;
    //CRC of the stuff before
    byte[] thing = {(byte) 16, receivedFrameNumber};
    nakFrame[3] = getCRC8(thing);
    //stop tag
    nakFrame[4] = stopTag;

    transmit(nakFrame);
  }
  // ===============================================================

  protected void sendACKFrame(byte num) {
    byte[] ackFrame = new byte[5];

    //start tag
    ackFrame[0] = startTag;
    //ACK byte = 0b00010001
    ackFrame[1] = (byte) 17;
    //frame number of error
    ackFrame[2] = num;
    //CRC of the stuff before
    byte[] thing = {(byte) 17, receivedFrameNumber};
    ackFrame[3] = getCRC8(thing);
    //stop tag
    ackFrame[4] = stopTag;

    transmit(ackFrame);
  }
  // ===============================================================

  /**
  * Extract the next frame-worth of data from the sending buffer, frame it,
  * and then send it.
  */
  protected void sendNextFrame() {

    // Extract a frame-worth of data from the sending buffer.
    int frameSize = ((sendBuffer.size() < MAX_FRAME_SIZE)
    ? sendBuffer.size()
    : MAX_FRAME_SIZE);
    byte[]         data = new byte[frameSize];
    Iterator<Byte>    i = sendBuffer.iterator();
    for (int j = 0; j < frameSize; j += 1) {
      data[j] = i.next();
      i.remove();
    }

    // Frame and transmit this chunk.
    byte[] framedData = createFrame(data);
    do {
      transmit(framedData);
    } while(!finishFrameSend());
  } // sendNextFrame ()

  // ===============================================================

  /**
  * Complete the process of sending a frame.  This method will examine the recieved data
  * from the response of the receiver, and return whether we can send the next frame
  * not.
  *
  * @return Return whether we need to send the frame again or not
  */
  protected boolean finishFrameSend() {
    //we can expect the response to be 2 bytes long, one with the
    //ACK/NAK frame and one with the CRC of that data to make sure
    //that is arrived okay
    Iterator<Byte> responseMessage;
    if(ackStuff != null) {
      responseMessage = ackStuff.iterator();
    } else {
        //something has went wrong. This should fix my null pointer exception
        return false;
    }

    if(responseMessage == null) {
      return false;
    }
    //parse all the information in the response frame
    byte messageConfirmation = responseMessage.next();
    byte frameConfirmation = responseMessage.next();
    byte CRC = responseMessage.next();
    byte[] stuff = {messageConfirmation, frameConfirmation};

    //now we can clear the ack buffer
    while(responseMessage.hasNext()) {
      try{
        responseMessage.remove();
      } catch (IllegalStateException e) {
        //I really don't know what to do with it
        //break i guess?
        break;
      }
    }

    //now we do our checks
    if((messageConfirmation & 0x1) == 0b00000001) {//then our message is a confirmation
      if(frameConfirmation == sentFrameNumber) {
        if(sentFrameNumber == 0) {
          sentFrameNumber = 1;
          //System.out.println("Setting sentFrameNumber to " + sentFrameNumber);
          return true;
        } else {
          sentFrameNumber = 0;
          //System.out.println("Setting sentFrameNumber to " + sentFrameNumber);
          return true;
        }
      } else {
        //there was something wrong with the ack
        //System.out.println("Frame number or CRC was wrong in ACK frame. Resending.");
        return false;
      }
      //advance the frame number, and return true
    } else if((messageConfirmation & 0x1) == 0){//then the receiver did not get the frame correctly, and we cannot advance
      //System.out.println("NAK Frame received. Sending last frame again");
      return false;
    } else {
      //something else is wrong. Just send the message again
      //System.out.println("Something really went wrong with ACK/NAK. Resending.");
      return false;
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
  //the boolean that dictates the frame number
  private byte sentFrameNumber = 0;
  private byte receivedFrameNumber = 0;

  //a queue that is only used when needed
  Queue<Byte> ackStuff;
  // ===================================================================
} // class DumbDataLinkLayer
// ===================================================================
