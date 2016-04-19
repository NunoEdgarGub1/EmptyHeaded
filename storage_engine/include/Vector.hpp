/******************************************************************************
*
* Author: Christopher R. Aberger
*
* TOP LEVEL CLASS FOR OUR SORTED VectorS.  PROVIDE A Vector OF GENERAL PRIMITIVE 
* TYPES THAT WE PROVIDE (PSHORT,UINT,VARIANT,BITPACKED,BITVector).  WE ALSO
* PROVIDE A HYBRID Vector IMPLEMENTATION THAT DYNAMICALLY CHOOSES THE TYPE
* FOR THE DEVELOPER. IMPLEMENTATION FOR PRIMITIVE TYPE OPS CAN BE 
* FOUND IN STATIC CLASSES IN THE LAYOUT FOLDER.
******************************************************************************/

#ifndef _VECTOR_H_
#define _VECTOR_H_

#include "utils/utils.hpp"
#include "vector/EHVector.hpp"
#include "vector/BLASVector.hpp"
#include "vector/Meta.hpp"
/*
Vectors are laid flat in the memory buffer as follows

---------------------------------------------------------
Meta | Indices (uint/bitset/block) | Annotations
---------------------------------------------------------
*/
template <class T, class A, class M>
struct Vector{ 
  //Construct this on your own (just manages the memory)
  BufferIndex bufferIndex;
  M* memoryBuffer;

  Vector(
    M* memory_buffer_in,
    const BufferIndex& restrict buffer_index_in)
  {
    memoryBuffer = memory_buffer_in;
    bufferIndex = buffer_index_in;
  }

  Vector(
    M* memory_buffer_in)
  {
    BufferIndex buffer_index_in;
    buffer_index_in.tid = NUM_THREADS;
    buffer_index_in.index = 0;
    memoryBuffer = memory_buffer_in;
    bufferIndex = buffer_index_in;
  }

  Vector(
    const size_t tid,
    M* memory_buffer_in,
    const Vector<T,A,M>& restrict v_in)
  {
    memoryBuffer = memory_buffer_in;
    const size_t cpy_size = T:: template get_num_bytes<A>(v_in.get_meta());
    const size_t index = memory_buffer_in->get_offset(tid);
    uint8_t *buf = memory_buffer_in->get_next(tid,cpy_size);
    memcpy((void*)buf,(void*)v_in.memoryBuffer->get_address(v_in.bufferIndex),cpy_size);
    bufferIndex.tid = tid;
    bufferIndex.index = index;
  }


  Vector(
    const size_t tid,
    M* memory_buffer_in,
    const size_t num_bytes)
  {
    memoryBuffer = memory_buffer_in;
    const size_t index = memory_buffer_in->get_offset(tid);
    uint8_t *buf = memory_buffer_in->get_next(tid,num_bytes);
    memset((void*)buf,(uint8_t)0,num_bytes);
    bufferIndex.tid = tid;
    bufferIndex.index = index;
  }

  Vector(
    const size_t tid,
    M* memory_buffer_in,
    const uint8_t * const restrict data,
    const size_t index_len,
    const size_t anno_len)
  {
    memoryBuffer = memory_buffer_in;
    const size_t index = memory_buffer_in->get_offset(tid);
    uint8_t *buf = memory_buffer_in->get_next(tid,(index_len+anno_len));
    memcpy((void*)buf,(void*)data,index_len);
    memset((void*)(buf+index_len),(uint8_t)0,anno_len);

    bufferIndex.tid = tid;
    bufferIndex.index = index;
  }

  Vector(
    const size_t tid,
    M* memory_buffer_in,
    const uint8_t * const restrict data,
    const size_t index_len,
    A* anno,
    const size_t anno_len,
    const size_t anno_offset)
  {
    memoryBuffer = memory_buffer_in;
    const size_t index = memory_buffer_in->get_offset(tid);
    uint8_t *buf = memory_buffer_in->get_next(tid,(index_len+anno_len));
    memcpy((void*)buf,(void*)data,index_len);
    A* anno_buf = T::template get_annotation<A,M>(anno_offset,memoryBuffer,bufferIndex);
    memcpy((void*)anno_buf,(void*)anno,anno_len);

    bufferIndex.tid = tid;
    bufferIndex.index = index;
  }

  uint8_t* get_this() const;

  //Find the index of a data elem.
  uint32_t indexOf(const uint32_t data) const;

  //calls index of then calls get below.
  A get(const uint32_t data) const;

  A* get_block(const uint32_t block_index) const;

  uint8_t* get_index_data() const;

  size_t get_num_index_bytes() const;

  template<class AA>
  size_t get_num_annotation_bytes() const {
    return T:: template get_num_annotation_bytes<AA>(
      T::get_meta(memoryBuffer,bufferIndex));
  }

  type::layout get_type() const;

  //look up a data value
  A get(
    const uint32_t index,
    const uint32_t data) const;

  //set an annotation value
  void set(
    const uint32_t index,
    const uint32_t data, 
    const A& value);

  //look up a data value
  bool contains(const uint32_t key) const;

  inline Meta* get_meta() const{
    return T::get_meta(memoryBuffer,bufferIndex);
  }

  //mutable loop (returns data and index)
  template<typename F>
  inline void foreach(F f) const{
    T:: template foreach<A,M>(
      f,
      memoryBuffer,
      bufferIndex);
  }

  //mutable loop (returns data and index)
  template<typename F>
  inline void foreach_index(F f) const{
    T:: template foreach_index<M>(
      f,
      memoryBuffer,
      bufferIndex);
  }

  //parallel iterator
  template<typename F>
  inline void parforeach_index(F f) const{
    T:: template parforeach_index<M>(
      f,
      memoryBuffer,
      bufferIndex);
  }

  //constructors
  static Vector<T,A,M> from_array(
    const size_t tid,
    M* memoryBuffer,
    const uint32_t * const data,
    const size_t len);

  //constructors
  static Vector<T,A,M> from_array(
    const size_t tid,
    M* memoryBuffer,
    const uint32_t * const data,
    const A * const values,
    const size_t len,
    const size_t anno_offset);
};

#endif