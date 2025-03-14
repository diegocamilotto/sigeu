package br.edu.utfpr.dv.sigeu.dao;

import javax.ejb.Stateless;

import org.hibernate.Query;

import br.edu.utfpr.dv.sigeu.entities.Teacher;
import br.edu.utfpr.dv.sigeu.persistence.HibernateDAO;

@Stateless
public class TeacherDAO extends HibernateDAO<Teacher> {

    @Override
    public Teacher encontrePorId(Integer id) {
	String hql = "from Teacher o where o.idTeacher = :id";
	Query q = session.createQuery(hql);
	q.setInteger("id", id);
	return (Teacher) q.uniqueResult();
    }

    @Override
    public String getNomeSequencia() {
	return "teacher";
    }

    @Override
    public void preCriacao(Teacher o) {
	Long id = this.gerarNovoId();
	o.setIdTeacher(id.intValue());
    }

    @Override
    public void preAlteracao(Teacher o) {

    }

}
