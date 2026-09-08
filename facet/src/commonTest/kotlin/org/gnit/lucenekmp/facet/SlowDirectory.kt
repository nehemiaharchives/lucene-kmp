/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.facet

import kotlin.math.ln
import kotlin.random.Random
import org.gnit.lucenekmp.store.*
import org.gnit.lucenekmp.util.ThreadInterruptedException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.InterruptedException

/** Test utility - slow directory */
// TODO: move to test-framework and sometimes use in tests?
class SlowDirectory : FilterDirectory {

  companion object { private const val IO_SLEEP_THRESHOLD = 50 }

  var random: Random?
  private var sleepMillis: Int

  fun setSleepMillis(sleepMillis: Int) {
    this.sleepMillis = sleepMillis
  }

  constructor(sleepMillis: Int, random: Random?) : super(ByteBuffersDirectory()) {
    this.sleepMillis = sleepMillis
    this.random = random
  }

  
  override fun createOutput(name: String, context: IOContext): IndexOutput {
    if (sleepMillis != -1) {
      return SlowIndexOutput(super.createOutput(name, context))
    }

    return super.createOutput(name, context)
  }

  
  override fun openInput(name: String, context: IOContext): IndexInput {
    if (sleepMillis != -1) {
      return SlowIndexInput(super.openInput(name, context))
    }
    return super.openInput(name, context)
  }

  
  fun doSleep(random: Random?, length: Int) {
    var sTime = if (length < 10) sleepMillis else (sleepMillis * ln(length.toDouble())).toInt()
    if (random != null) {
      sTime = random.nextInt(sTime)
    }
    try {
      Thread.sleep(sTime.toLong())
    } catch (e: InterruptedException) {
      throw ThreadInterruptedException(e)
    }
  }

  /** Make a private random. */
  fun forkRandom(): Random? {
    if (random == null) {
      return null
    }
    return Random(random!!.nextLong())
  }

  /** Delegate class to wrap an IndexInput and delay reading bytes by some specified time. */
  private inner class SlowIndexInput : FilterIndexInput {
    private var numRead = 0
    private val rand: Random?

    constructor(ii: IndexInput) : super("SlowIndexInput(" + ii + ")", ii) {
      this.rand = forkRandom()
    }

    
    override fun readByte(): Byte {
      if (numRead >= IO_SLEEP_THRESHOLD) {
        doSleep(rand, 0)
        numRead = 0
      }
      ++numRead
      return `in`.readByte()
    }

    
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
      if (numRead >= IO_SLEEP_THRESHOLD) {
        doSleep(rand, len)
        numRead = 0
      }
      numRead += len
      `in`.readBytes(b, offset, len)
    }

    
    override fun equals(o: Any?): Boolean {
      return `in`.equals(o)
    }

    
    override fun hashCode(): Int {
      return `in`.hashCode()
    }
  }

  /** Delegate class to wrap an IndexOutput and delay writing bytes by some specified time. */
  private inner class SlowIndexOutput : FilterIndexOutput {

    private var numWrote = 0
    private val rand: Random?

    constructor(out: IndexOutput) : super("SlowIndexOutput(" + out + ")", out.name, out) {
      this.rand = forkRandom()
    }

    
    override fun writeByte(b: Byte) {
      if (numWrote >= IO_SLEEP_THRESHOLD) {
        doSleep(rand, 0)
        numWrote = 0
      }
      ++numWrote
      out!!.writeByte(b)
    }

    
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
      if (numWrote >= IO_SLEEP_THRESHOLD) {
        doSleep(rand, length)
        numWrote = 0
      }
      numWrote += length
      out!!.writeBytes(b, offset, length)
    }
  }
}
