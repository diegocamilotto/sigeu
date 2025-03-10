package br.edu.utfpr.dv.sigeu.dao;

import javax.ejb.Stateless;

import org.hibernate.Query;

import br.edu.utfpr.dv.sigeu.entities.Card;
import br.edu.utfpr.dv.sigeu.persistence.HibernateDAO;

@Stateless
public class CardDAO extends HibernateDAO<Card> {

    @Override
    public Card encontrePorId(Integer id) {
	String hql = "from Card o where o.idCard = :id";
	Query q = session.createQuery(hql);
	q.setInteger("id", id);
	return (Card) q.uniqueResult();
    }

    @Override
    public String getNomeSequencia() {
	return "card";
    }

    @Override
    public void preCriacao(Card o) {
	Integer val = this.gerarNovoId().intValue();
	o.setIdCard(val);
    }

    @Override
    public void preAlteracao(Card o) {

    }

}
