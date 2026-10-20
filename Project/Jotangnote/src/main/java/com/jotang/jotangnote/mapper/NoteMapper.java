package com.jotang.jotangnote.mapper;

import com.jotang.jotangnote.entity.Note;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMapper {

    @Insert("INSERT INTO notes(content, created_at,user_id) VALUES(#{content}, #{createdAt},#{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Note note);

    @Select("SELECT * FROM notes WHERE id = #{id}")
    Note findById(Integer id);

    @Update("UPDATE notes SET content = #{content} WHERE id = #{id}")
    void update(Note note);

    @Delete("DELETE FROM notes WHERE id = #{id}")
    void deleteById(Integer id);

    @Select("SELECT * FROM notes")
    List<Note> findAll();
}