package ru.daniel.soviethifi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.daniel.soviethifi.audio.*;
import ru.daniel.soviethifi.storage.*;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temp;
    private byte[] audio(int length){byte[] bytes=new byte[length];for(int i=3;i<length;i++)bytes[i]=(byte)(i*31);bytes[0]='I';bytes[1]='D';bytes[2]='3';return bytes;}
    @Test void attenuationIsMonotonicAndEndsAtRadius(){float previous=1;for(int d=0;d<=64;d++){float v=AudioMath.distanceGain(d,64);assertTrue(v<=previous);assertTrue(v>=0);previous=v;}assertEquals(0,previous);}
    @Test void wallsReduceLevelAndTreble(){assertTrue(AudioMath.wallGain(3)<AudioMath.wallGain(1));assertTrue(AudioMath.cutoff(3,.4f)<AudioMath.cutoff(1,.4f));assertTrue(AudioMath.cutoff(100,1)>=450);}
    @Test void silenceRadiusAndNegativeDistanceAreSafe(){assertEquals(0,AudioMath.distanceGain(0,0));assertEquals(1,AudioMath.distanceGain(-1,64),.001);}
    @Test void joiningLateSeeksToCurrentFrame(){assertEquals(441000,AudioMath.frameAt(1200,1000,44100));assertEquals(0,AudioMath.frameAt(900,1000,44100));}
    @Test void clippingIsBoundedAndSymmetric(){assertTrue(AudioMath.softClip(20)<=1);assertEquals(-AudioMath.softClip(2),AudioMath.softClip(-2));}
    @Test void stereoInterpolationPreservesChannels(){var p=new PcmTrack(new short[]{0,10000,20000,0,0,0},44100);assertEquals(10000/32768f,p.sample(.5,0),.00001);assertEquals(5000/32768f,p.sample(.5,1),.00001);assertEquals(0,p.sample(999,0));}
    @Test void sequentialChunksRoundTrip(){byte[] input=audio(40000);var a=new ChunkAssembly(input.length);for(int pos=0;pos<input.length;pos+=TrackRules.CHUNK)a.append(pos,Arrays.copyOfRange(input,pos,Math.min(input.length,pos+TrackRules.CHUNK)));assertTrue(a.complete());assertArrayEquals(input,a.finish(TrackRules.hash(input)));}
    @Test void rejectsOversizedAllocation(){assertThrows(IllegalArgumentException.class,()->new ChunkAssembly(TrackRules.MAX_BYTES+1));assertThrows(IllegalArgumentException.class,()->new ChunkAssembly(-1));}
    @Test void rejectsWrongOffsetsWithoutMutating(){var a=new ChunkAssembly(64);assertThrows(IllegalArgumentException.class,()->a.append(1,new byte[32]));assertEquals(0,a.received());a.append(0,new byte[32]);assertThrows(IllegalArgumentException.class,()->a.append(0,new byte[32]));assertEquals(32,a.received());}
    @Test void rejectsEmptyOversizedAndOverflowChunks(){var a=new ChunkAssembly(64);assertThrows(IllegalArgumentException.class,()->a.append(0,new byte[0]));assertThrows(IllegalArgumentException.class,()->a.append(0,new byte[65]));assertThrows(IllegalArgumentException.class,()->a.append(0,new byte[TrackRules.CHUNK+1]));}
    @Test void requiresCompletionAndMatchingChecksum(){var a=new ChunkAssembly(64);assertThrows(IllegalStateException.class,()->a.finish("x"));a.append(0,new byte[64]);assertThrows(IllegalArgumentException.class,()->a.finish("0".repeat(64)));}
    @Test void trackIdsCannotEscapeStorage(){assertFalse(TrackRules.validId("../world.dat"));assertFalse(TrackRules.validId("A".repeat(64)));assertTrue(TrackRules.validId(TrackRules.hash(new byte[1])));}
    @Test void labelsStripControlCodesAndLimitLength(){assertEquals("abc",TrackRules.title("a\nb§c"));assertEquals(80,TrackRules.title("x".repeat(100)).length());assertEquals("Кассета",TrackRules.title("\n"));}
    @Test void headersRejectWrongFormats(){assertTrue(TrackRules.audioHeader(audio(64),"mp3"));assertFalse(TrackRules.audioHeader(audio(64),"ogg"));assertFalse(TrackRules.audioHeader(new byte[64],"wav"));}
    @Test void storageSurvivesRestartAndDeduplicates()throws Exception{byte[] bytes=audio(64);String id=TrackRules.hash(bytes);var s=new TrackStore(temp);var m=new TrackStore.Meta(id,"Test","mp3",64,10);s.put(m,bytes);s.put(m,bytes);assertEquals(64,s.used());var reload=new TrackStore(temp);assertEquals("Test",reload.get(id).title());assertArrayEquals(bytes,reload.read(id));}
    @Test void storageRejectsCorruption()throws Exception{var s=new TrackStore(temp);byte[] bytes=audio(64);var m=new TrackStore.Meta("0".repeat(64),"Test","mp3",64,10);assertThrows(java.io.IOException.class,()->s.put(m,bytes));assertEquals(0,s.used());}
}
