// =============================================================================
// IMPORTS

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.lang.System;
// =============================================================================


// =============================================================================
/**
 * @file   PARDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   February 2020
 *
 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs error management with a parity bit.  It employs no
 * flow control; damaged frames are dropped.
 */
public class PARDataLinkLayer extends DataLinkLayer {
// =============================================================================


 
    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    
	protected Queue<Byte> createFrame (Queue<Byte> data) {

    	// ***THINGS TO ADD*** 1) frame number 2) whether frame is ack

	// Calculate the parity.
	byte parity = calculateParity(data);
	
	// Begin with the start tag
	Queue<Byte> framingData = new LinkedList<Byte>();
	framingData.add(startTag);

    	/* add frame number */
    	framingData.add(frameNumber);

	// Add each byte of original data.
    	int index = 0;
        for (byte currentByte : data) {

	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
	    if ((currentByte == startTag) ||
		(currentByte == stopTag) ||
		(currentByte == escapeTag)) {

		framingData.add(escapeTag);

	    }

        // if sender's first data byte is an ack tag, precede it with an escape tag
        if (!receiver && index == 0 && currentByte == ackTag){
        framingData.add(escapeTag);
        }

        index += 1;

	    // Add the data byte itself.
	    framingData.add(currentByte);
	}

	// Add the parity byte.
	framingData.add(parity);
	
	// End with a stop tag.
	framingData.add(stopTag);

	return framingData;
	
    	} // createFrame ()
    	// =========================================================================

	
	
	// =========================================================================
    /**
     * After sending a frame, set GLOBAL waiting variable to true.
     * Return null whenever the host is waiting for an acknowledgment. 
     */

    	protected Queue<Byte> sendNextFrame () {

    	/* check if we are waiting for an acknowledgment */
    	if (!waiting){

        /* if no frame sent recently, send current frame as usual */
            if (sentFrameBuffer.size() == 0){
                return super.sendNextFrame();
            }
            else {
            return sentFrameBuffer;
            }
    	}
    	else {
    	   return null;
    	}
    } // sendNextFrame ()
	// =========================================================================
    
	
	
	
     // =========================================================================
    /**
     * After sending a frame, do any bookkeeping (e.g., buffer the frame in case
     * a resend is required).
     *
     * @param frame The framed data that was transmitted.
     */ 
    protected void finishFrameSend (Queue<Byte> frame) {

        /* record the time frame was sent */
        lastFrameTime = System.currentTimeMillis();

        /* buffer the frame's contents in case resend is needed */
        sentFrameBuffer = frame;

        waiting = true;

        // COMPLETE ME WITH FLOW CONTROL
    } // finishFrameSend ()
    // =========================================================================

	
	
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

    protected Queue<Byte> processFrame () {

	// Search for a start tag.  Discard anything prior to it.
	boolean        startTagFound = false;
	Iterator<Byte>             i = receiveBuffer.iterator();
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
    	int                       index = 1;
	LinkedList<Byte> extractedBytes = new LinkedList<Byte>();
	boolean            stopTagFound = false;
    	boolean            startTagFound = true;
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
            index += 1;
	    if (current == escapeTag) {
		if (i.hasNext()) {
		    current = i.next();
                    index += 1;
		    extractedBytes.add(current);
		} else {
		    // An escape was the last byte available, so this is not a
		    // complete frame.
		    return null;
		}
	    } else if (current == stopTag) {
		cleanBufferUpTo(index);
		stopTagFound = true;
	    } else if (current == startTag) {
		cleanBufferUpTo(index - 1);
                index = 1;
                startTagFound = true;
		extractedBytes = new LinkedList<Byte>();
        }
        //add data 
        else{
		extractedBytes.add(current);
	    }

	}

	// If there is no stop tag, then the frame is incomplete.
	if (!stopTagFound) {
	    return null;
	}

	if (debug) {
	    System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
	}


    // the first byte inside the frame is the frame number
    byte foundFrameNumber = extractedBytes.remove(0);

    // CURRENT HOST IS SENDER
    if (!receiver){

    // if correct frame was received, increment frame count
    if (foundFrameNumber == frameNumberCounter){
    frameNumberCounter += 1;

    //clear sent frame buffer
        while (sentFrameBuffer.peek() != null){
        sentFrameBuffer.remove();
        }
    } else{
    // if acknowledgment for wrong frame is received, resend 
    if (foundFrameNumber != frameNumberCounter){
    return null;
    }
    }
    waiting = false;
    }

    // CURRENT HOST IS RECEIVER
    else {
    
    if (foundFrameNumber == frameNumberCounter){
    frameNumberCounter += 1;
    } 

    /* if receiver's previous acknowledgement didn't go through, reset 
    frame count to align with most recently received frame <--- THIS PART IS SUS,
    IS THIS THE RIGHT WAY TO HANDLE?  */
    if (foundFrameNumber < frameNumberCounter){
    frameNumberCounter = foundFrameNumber;
        }
        }
        
	// The final byte inside the frame is the parity.  Compare it to a
	// recalculation.
	byte receivedParity   = extractedBytes.remove(extractedBytes.size() - 1);
	byte calculatedParity = calculateParity(extractedBytes);
	if (receivedParity != calculatedParity) {
	    System.out.printf("ParityDataLinkLayer.processFrame():\tDamaged frame\n");
	    return null;
	}

    /* if the received frame is not an acknowledgment, we know that the current host is a receiver */
    if (extractedBytes.getFirst() != ackTag){
    receiver = true;
    }
	return extractedBytes;

    } // processFrame ()
    // =========================================================================

    

    // =========================================================================
    /**
     * After receiving a frame, do any bookkeeping (e.g., deliver the frame to
     * the client, if appropriate) and responding (e.g., send an
     * acknowledgment).
     *
     * @param frame The frame of bytes received.
     */
    protected void finishFrameReceive (Queue<Byte> frame) {
    	// will be responsible for sending an ACK when it receives a frame
    	// also responsible for recognizing that what it has received is an ACK frame
    	//and letting the DLL move into a state of "ready to send the next data frame".

    	// check frame number? and increment if correct then send ack?

        // COMPLETE ME WITH FLOW CONTROL
        
        // Deliver frame to the client.
        byte[] deliverable = new byte[frame.size()];
        for (int i = 0; i < deliverable.length; i += 1) {
            deliverable[i] = frame.remove();
        }

        client.receive(deliverable);
        // if this host is a receiver, create and send ack frame to sender
        if (receiver){

        /* initialize ackStatus to contain ackTag */
        if (ackStatus.size() == 0){
        ackStatus.add(ackTag);
        }

        /* create frame incorporating ackTag, send it */
        Queue<Byte> ackFrame= new LinkedList<Byte>();
        ackFrame = createFrame(ackStatus);
        transmit(ackFrame);
        }
    } // finishFrameReceive ()
    // =========================================================================



    // =========================================================================
    /**
     * Determine whether a timeout should occur and be processed.  This method
     * is called regularly in the event loop, and should check whether too much
     * time has passed since some kind of response is expected.
     */
    protected void checkTimeout (long timeStart) {
    	long time = System.currentTimeMillis();
    	if (time - timeStart > 2000) {
    		/* setting waiting = false will trigger resend - 
		sendNextFrame will see that there is a frame in the 
		buffer and it will resend it */
    		waiting = false;
    	}
    	else {
    		// keep going
    	}

    } // checkTimeout ()
    // =========================================================================

    protected void checkTimeout () {
        //do nothing - used to ensure PAR is a child class
    }


    // =========================================================================
    /**
     * For a sequence of bytes, determine its parity.
     *
     * @param data The sequence of bytes over which to calculate.
     * @return <code>1</code> if the parity is odd; <code>0</code> if the parity
     *         is even.
     */
    private byte calculateParity (Queue<Byte> data) {

	int parity = 0;
	for (byte b : data) {
	    for (int j = 0; j < Byte.SIZE; j += 1) {
		if (((1 << j) & b) != 0) {
		    parity ^= 1;
		}
	    }
	}

	return (byte)parity;
	
    } // calculateParity ()
    // =========================================================================
    


    // =========================================================================
    /**
     * Remove a leading number of elements from the receive buffer.
     *
     * @param index The index of the position up to which the bytes are to be
     *              removed.
     */
    private void cleanBufferUpTo (int index) {

        for (int i = 0; i < index; i += 1) {
            receiveBuffer.remove();
	}

    } // cleanBufferUpTo ()
    // =========================================================================



    // =========================================================================
    // DATA MEMBERS

    /** The start tag. */
    private final byte startTag  = (byte)'{';

    /** The stop tag. */
    private final byte stopTag   = (byte)'}';

    /** The escape tag. */
    private final byte escapeTag = (byte)'\\';
    // =========================================================================

    private final byte ackTag = (byte)'!';

    /* check if sender needs to wait before re-sending frame */
    protected boolean waiting = false;

    /* hold a copy of frame in case of t/o, or ack isn't received */
    protected Queue<Byte> sentFrameBuffer = new LinkedList<Byte>();

    /* record time of last time frame was sent */
    protected long lastFrameTime = 0L;

    protected Boolean receiver = false;

    protected byte ackTag = (byte)'!';

    protected Queue<Byte> ackStatus = new LinkedList<Byte>();
	
    /* The frame number (sender and receiver each have own)*/
    protected byte frameNumber = 0;


// =============================================================================
} // class PARDataLinkLayer
// =============================================================================

