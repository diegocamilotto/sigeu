package br.edu.utfpr.dv.sigeu.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import com.adamiworks.utils.DateTimeUtils;

import org.hibernate.Hibernate;

import br.edu.utfpr.dv.sigeu.dao.ItemReservaDAO;
import br.edu.utfpr.dv.sigeu.dao.PeriodDAO;
import br.edu.utfpr.dv.sigeu.dao.ReservaDAO;
import br.edu.utfpr.dv.sigeu.entities.Campus;
import br.edu.utfpr.dv.sigeu.entities.CategoriaItemReserva;
import br.edu.utfpr.dv.sigeu.entities.ItemReserva;
import br.edu.utfpr.dv.sigeu.entities.Period;
import br.edu.utfpr.dv.sigeu.entities.Pessoa;
import br.edu.utfpr.dv.sigeu.entities.Reserva;
import br.edu.utfpr.dv.sigeu.entities.TipoReserva;
import br.edu.utfpr.dv.sigeu.entities.Transacao;
import br.edu.utfpr.dv.sigeu.enumeration.RepeticaoReservaEnum;
import br.edu.utfpr.dv.sigeu.enumeration.StatusReserva;
import br.edu.utfpr.dv.sigeu.exception.ExisteReservaConcorrenteException;
import br.edu.utfpr.dv.sigeu.vo.ReservaVO;

@Stateless
public class ReservaService {

    @EJB
    private TransacaoService transacaoService;

    @EJB
    private EmailService emailService;

    @EJB
    private ReservaDAO reservaDAO;

    @EJB
    private ItemReservaDAO itemDAO;

    @EJB
    private PeriodDAO periodDAO;

    public Reserva encontrePorId(Integer idReserva) {
	return reservaDAO.encontrePorId(idReserva);
    }

    public void criar(Campus campus, Pessoa pessoaLogin, Reserva reserva) throws Exception {
	boolean usuarioLoginAutorizador = false;

	if (reserva.getCor() == null) {
	    reserva.setCor("#BBD2D2");
	}

	reserva.setDataGravacao(Calendar.getInstance().getTime());
	reserva.setHoraGravacao(Calendar.getInstance().getTime());

	// Para todos os efeitos, até que seja cancelada ou efetivada, o
	// autorizador é quem gravou a reserva.
	reserva.setIdAutorizador(reserva.getIdPessoa());

	Transacao transacao = transacaoService.criar(campus, pessoaLogin, "Reserva do item " + reserva.getIdItemReserva().getNome());
	reserva.setIdTransacao(transacao);

	ItemReserva ir = itemDAO.encontrePorId(reserva.getIdItemReserva().getIdItemReserva());

	reserva.setStatus(StatusReserva.EFETIVADA.getStatus());

	// A lista de pessoas refere-se à lista de autorizadores
	if (ir.getPessoaList() != null && ir.getPessoaList().size() > 0) {
	    // Ops! Tem pelo menos um autorizador. Se o usuário não for um
	    // deles, grava como pendente

	    Pessoa usuarioLogin = pessoaLogin;

	    for (Pessoa p : ir.getPessoaList()) {
		if (usuarioLogin.getIdPessoa() == p.getIdPessoa()) {
		    usuarioLoginAutorizador = true;
		    break;
		}
	    }

	    if (!usuarioLoginAutorizador) {
		reserva.setStatus(StatusReserva.PENDENTE.getStatus());
	    }
	}

	reservaDAO.criar(reserva);

	if (!usuarioLoginAutorizador) {
	    List<Pessoa> autorizadores = reserva.getIdItemReserva().getPessoaList();

	    for (Pessoa a : autorizadores) {
		emailService.enviaEmailAutorizador(campus, a, reserva.getIdItemReserva());
	    }
	}
    }

    /**
     * Altera uma lista de reservas
     * 
     * @param listaReserva
     * @param motivoTransacao
     * @throws Exception
     */
    public void alterar(Campus campus, Pessoa pessoaLogin, List<Reserva> listaReserva, String motivoTransacao) throws Exception {
	Transacao transacao = transacaoService.criar(campus, pessoaLogin, motivoTransacao);

	for (Reserva reserva : listaReserva) {
	    StatusReserva statusReserva = StatusReserva.getFromStatus(reserva.getStatus());

	    switch (statusReserva) {
		case EFETIVADA:
		    if (existeConcorrente(reserva)) {
			throw new ExisteReservaConcorrenteException(reserva);
		    }
		    break;

		default:
		    break;
	    }

	    reserva.setIdTransacao(transacao);
	    reservaDAO.alterar(reserva);
	}

    }

    /**
     * Duplica um objeto de reserva
     * 
     * @param reserva
     * @return
     */
    public Reserva duplicar(Reserva reserva) {
	Reserva r = new Reserva();
	r.setCor(reserva.getCor());
	r.setData(reserva.getData());
	r.setEmailNotificacao(reserva.getEmailNotificacao());
	r.setHoraFim(reserva.getHoraFim());
	r.setHoraInicio(reserva.getHoraInicio());
	r.setIdCampus(reserva.getIdCampus());
	r.setIdItemReserva(reserva.getIdItemReserva());
	r.setIdPessoa(reserva.getIdPessoa());
	r.setIdTipoReserva(reserva.getIdTipoReserva());
	r.setIdTransacao(reserva.getIdTransacao());
	r.setIdUsuario(reserva.getIdUsuario());
	r.setNomeUsuario(reserva.getNomeUsuario());
	r.setMotivo(reserva.getMotivo());
	r.setRotulo(reserva.getRotulo());
	r.setStatus(reserva.getStatus());
	r.setMotivoCancelamento(reserva.getMotivoCancelamento());
	r.setDataGravacao(reserva.getDataGravacao());
	r.setHoraGravacao(reserva.getHoraGravacao());
	r.setIdAutorizador(reserva.getIdAutorizador());
	r.setImportado(reserva.getImportado());

	return r;
    }

    /**
     * Grava uma reserva de forma recorrente até uma data limite.
     * 
     * @param reserva
     * @param tipoRecorrencia
     * @param dataLimite
     * @throws Exception
     */
    public List<Reserva> criarRecorrente(Campus campus, Pessoa pessoaLogin, Reserva reserva, RepeticaoReservaEnum tipoRecorrencia, Date dataLimite)
	    throws Exception {

	boolean usuarioLoginAutorizador = false;

	reserva.setDataGravacao(Calendar.getInstance().getTime());
	reserva.setHoraGravacao(Calendar.getInstance().getTime());

	if (reserva.getCor() == null) {
	    reserva.setCor("#BBD2D2");
	}

	// Para todos os efeitos, até que seja cancelada ou efetivada, o
	// autorizador é quem gravou a reserva.
	reserva.setIdAutorizador(reserva.getIdPessoa());

	if (tipoRecorrencia.equals(RepeticaoReservaEnum.SEMANAL)) {
	    Transacao transacao = transacaoService.criar(campus, pessoaLogin, "Reserva do item " + reserva.getIdItemReserva().getNome() + " semanal");
	    reserva.setIdTransacao(transacao);

	    ItemReserva ir = itemDAO.encontrePorId(reserva.getIdItemReserva().getIdItemReserva());

	    reserva.setStatus(StatusReserva.EFETIVADA.getStatus());

	    // A lista de pessoas refere-se à lista de autorizadores
	    if (ir.getPessoaList() != null && ir.getPessoaList().size() > 0) {
		// Ops! Tem pelo menos um autorizador. Se o usuário não for
		// um
		// deles, grava como pendente

		Pessoa usuarioLogin = pessoaLogin;

		for (Pessoa p : ir.getPessoaList()) {
		    if (usuarioLogin.getIdPessoa() == p.getIdPessoa()) {
			usuarioLoginAutorizador = true;
			break;
		    }
		}

		if (!usuarioLoginAutorizador) {
		    reserva.setStatus(StatusReserva.PENDENTE.getStatus());
		}
	    }

	    Calendar calLimite = Calendar.getInstance();
	    calLimite.setTime(dataLimite);

	    Calendar calData = Calendar.getInstance();
	    calData.setTime(reserva.getData());
	    int diaDaSemana = calData.get(Calendar.DAY_OF_WEEK);

	    // Incrementa 1 dia para começar o laço
	    calData.add(Calendar.DAY_OF_MONTH, 1);

	    boolean existeConcorrencia = false;

	    List<Reserva> listaGravacao = new ArrayList<Reserva>();

	    while (true) {
		if (calData.getTimeInMillis() > calLimite.getTimeInMillis()) {
		    break;
		}

		int diaDaSemanaAtual = calData.get(Calendar.DAY_OF_WEEK);

		if (diaDaSemanaAtual == diaDaSemana) {
		    Reserva r = duplicar(reserva);
		    r.setData(calData.getTime());

		    existeConcorrencia = existeConcorrente(r);

		    if (!existeConcorrencia) {
			listaGravacao.add(r);
		    } else {
			break;
		    }
		}

		calData.add(Calendar.DAY_OF_MONTH, 1);
	    }

	    if (existeConcorrencia) {
		throw new ExisteReservaConcorrenteException(reserva);
	    } else {
		// Grava a reserva inicial
		listaGravacao.add(reserva);

		for (Reserva r : listaGravacao) {
		    reservaDAO.criar(r);
		}
	    }

	    if (!usuarioLoginAutorizador) {
		List<Pessoa> autorizadores = reserva.getIdItemReserva().getPessoaList();

		for (Pessoa a : autorizadores) {
		    emailService.enviaEmailAutorizador(campus, a, reserva.getIdItemReserva());
		}
	    }

	    return listaGravacao;
	}
	return null;
    }

    /**
     * Confere se já existe reserva concorrente
     * 
     * @param reserva
     * @return
     * @throws Exception
     */
    public boolean existeConcorrente(Reserva reserva) throws Exception {
	List<ItemReserva> lista = itemDAO.pesquisaItemReservaDisponivel(reserva.getIdCampus(), reserva.getData(), reserva.getHoraInicio(), reserva.getHoraFim(),
		reserva.getIdItemReserva().getIdCategoria(), reserva.getIdItemReserva());

	if (lista != null && lista.size() == 0) {
	    return true;
	}

	return false;
    }

    /**
     * Busca todas as reservas do dia para uma categoria e item, se ele não estiver
     * null
     * 
     * @param data
     * @param tipoReserva
     * @param categoria
     * @param item
     * @param usuario
     * @return
     * @throws Exception
     */
    public List<Reserva> pesquisaReservasEfetivadas(Campus campus, Date data, Date horaI, Date horaF, TipoReserva tipoReserva, CategoriaItemReserva categoria,
	    ItemReserva item, String usuario) throws Exception {
	List<Reserva> lista = reservaDAO.pesquisaReserva(campus, StatusReserva.EFETIVADA, data, horaI, horaF, tipoReserva, categoria, item, usuario);

	if (lista != null && lista.size() > 0) {
	    for (Reserva r : lista) {
		// Hibernate.initialize(r.getIdItemReserva());
		Hibernate.initialize(r.getIdItemReserva().getIdCategoria());
		Hibernate.initialize(r.getIdTipoReserva());
		Hibernate.initialize(r.getIdUsuario());
		Hibernate.initialize(r.getIdPessoa());
		Hibernate.initialize(r.getIdTransacao());
	    }
	}

	return lista;
    }

    public List<Reserva> pesquisaReservasEfetivadas(Campus campus, Date data, Date data2, Date horaI, Date horaF, TipoReserva tipoReserva,
	    CategoriaItemReserva categoria, ItemReserva item, String usuario, String motivo, boolean incluiItemDesativado) throws Exception {

	List<Reserva> lista = reservaDAO.pesquisaReserva(campus, StatusReserva.EFETIVADA, data, data2, horaI, horaF, tipoReserva, categoria, item, usuario,
		motivo, incluiItemDesativado);

	if (lista != null && lista.size() > 0) {
	    for (Reserva r : lista) {
		// Hibernate.initialize(r.getIdItemReserva());
		Hibernate.initialize(r.getIdItemReserva().getIdCategoria());
		Hibernate.initialize(r.getIdTipoReserva());
		Hibernate.initialize(r.getIdUsuario());
		Hibernate.initialize(r.getIdPessoa());
		Hibernate.initialize(r.getIdTransacao());
	    }
	}

	return lista;

    }

    /**
     * Busca todos os itens que estão disponíveis para a data e horário, categoria e
     * item (Se não estiver null)
     * 
     * @param data
     * @param horaInicial
     * @param horaFinal
     * @param categoria
     * @param item
     * @return
     * @throws Exception
     */
    public List<ItemReserva> pesquisaItemReservaDisponivel(Campus campus, Date data, Date horaInicial, Date horaFinal, CategoriaItemReserva categoria,
	    ItemReserva item) throws Exception {

	List<ItemReserva> lista = itemDAO.pesquisaItemReservaDisponivel(campus, data, horaInicial, horaFinal, categoria, item);

	if (lista != null && lista.size() > 0) {
	    for (ItemReserva i : lista) {
		Hibernate.initialize(i.getIdCategoria());
		Hibernate.initialize(i.getPessoaList());
	    }
	}

	return lista;
    }

    /**
     * Retorna uma lista de todas as reservas de uma data feitas por uma pessoa.
     * 
     * @param pessoa      Não pode ser null
     * @param dataInicial Não pode ser null
     * @param categoria   pode ser null
     * @param item        poder ser null
     * @param importadas
     * @return
     * @throws Exception
     */
    public List<Reserva> pesquisaReservasEfetivadasDoUsuario(Campus campus, Pessoa pessoa, Date dataInicial, Date dataFinal, CategoriaItemReserva categoria,
	    ItemReserva item, boolean importadas) throws Exception {
	List<Reserva> lista = reservaDAO.pesquisaReservaDoUsuario(campus, StatusReserva.EFETIVADA, pessoa, dataInicial, dataFinal, categoria, item, importadas);

	if (lista != null && lista.size() > 0) {
	    for (Reserva r : lista) {
		// Hibernate.initialize(r.getIdItemReserva());
		Hibernate.initialize(r.getIdItemReserva().getIdCategoria());
		Hibernate.initialize(r.getIdTipoReserva());
		Hibernate.initialize(r.getIdTransacao());
		Hibernate.initialize(r.getIdAutorizador());
	    }
	}

	return lista;
    }

    public Reserva pesquisaReservaPorId(Integer id) throws Exception {
	return reservaDAO.encontrePorId(id);
    }

    public void excluir(Reserva r) throws Exception {
	reservaDAO.remover(r);
    }

    /**
     * Altera o stats de uma reserva
     * 
     * @param r
     * @param status
     * @throws Exception
     */
    public void modificaStatus(Reserva r, StatusReserva status) throws Exception {
	r.setStatus(status.getStatus());
	reservaDAO.alterar(r);
    }

    /**
     * Atalho para cancelar reservas
     * 
     * @param r
     * @param motivo
     * @throws Exception
     */
    public void cancelaReserva(Reserva r, String motivo) throws Exception {
	r.setMotivoCancelamento(motivo);
	modificaStatus(r, StatusReserva.CANCELADA);
    }

    /**
     * Lista as reservas do Campus conforme filtros
     * 
     * @param data
     * @param horaInicial
     * @param horaFinal
     * @param categoria
     * @param item
     * @return
     * @throws Exception
     */
    public List<Reserva> pesquisaReservasEfetivadas(Campus campus, Date data, Date horaInicial, Date horaFinal, CategoriaItemReserva categoria,
	    ItemReserva item) throws Exception {
	List<Reserva> lista = reservaDAO.pesquisaReserva(campus, StatusReserva.EFETIVADA, data, horaInicial, horaFinal, categoria, item);

	if (lista != null && lista.size() > 0) {
	    for (Reserva r : lista) {
		// Hibernate.initialize(r.getIdItemReserva());
		Hibernate.initialize(r.getIdItemReserva().getIdCategoria());
		Hibernate.initialize(r.getIdTransacao());
	    }
	}

	return lista;
    }

    public List<Period> getAllPeriods(Campus campus) throws Exception {
	return periodDAO.getAll(campus);
    }

    public List<Reserva> pesquisaReservasEfetivadas(Campus campus, Date data, TipoReserva tipoReserva) throws Exception {
	List<Reserva> lista = reservaDAO.pesquisaReserva(campus, StatusReserva.EFETIVADA, data, tipoReserva);

	if (lista != null && lista.size() > 0) {
	    for (Reserva r : lista) {
		// Hibernate.initialize(r.getIdItemReserva());
		Hibernate.initialize(r.getIdItemReserva().getIdCategoria());
		Hibernate.initialize(r.getIdTransacao());
	    }
	}

	return lista;
    }

    /**
     * Com base em uma lista de itens, confere se há disponibilidade na data
     * solicitada e na recorrência solicitada.
     * 
     * @param data
     * @param horaInicio
     * @param horaFim
     * @param dataLimite
     * @param tipoRecorrencia
     * @param listaItemReserva
     * @return
     * @throws Exception
     */
    public List<ItemReserva> removeItensNaoDisponiveisParaReservaRecorrente(Campus campus, Pessoa pessoaLogin, Date data, Date horaInicio, Date horaFim,
	    RepeticaoReservaEnum tipoRecorrencia, Date dataLimite, List<ItemReserva> listaItemReserva) throws Exception {

	if (!tipoRecorrencia.equals(RepeticaoReservaEnum.SEM_REPETICAO)) {
	    // Somente se o tipo de recorrência for diferente de SEM_REPETICAO
	    List<ItemReserva> listaDeItensARemover = new ArrayList<ItemReserva>();

	    Calendar calendarLimite = Calendar.getInstance();
	    calendarLimite.setTime(dataLimite);

	    Calendar calendarAtual = Calendar.getInstance();
	    calendarAtual.setTime(data);

	    // Zera hora, minuto, segundo e milissegundo para
	    // garantir que o teste do While seja true até o último dia
	    calendarAtual.set(Calendar.HOUR_OF_DAY, 0);
	    calendarAtual.set(Calendar.MINUTE, 0);
	    calendarAtual.set(Calendar.SECOND, 0);
	    calendarAtual.set(Calendar.MILLISECOND, 0);
	    calendarLimite.set(Calendar.HOUR_OF_DAY, 0);
	    calendarLimite.set(Calendar.MINUTE, 0);
	    calendarLimite.set(Calendar.SECOND, 0);
	    calendarLimite.set(Calendar.MILLISECOND, 0);

	    int diaDaSemana = calendarAtual.get(Calendar.DAY_OF_WEEK);

	    while (true) {
		long milissecondsCalendarAtual = calendarAtual.getTimeInMillis();
		long milissecondsCalendarLimite = calendarLimite.getTimeInMillis();

		if (milissecondsCalendarAtual > milissecondsCalendarLimite) {
		    break;
		}

		// System.out.println("---");
		// System.out.println("Conferindo: " + calendarAtual.getTime() +
		// " <= " + dataLimite);

		int diaDaSemanaAtual = calendarAtual.get(Calendar.DAY_OF_WEEK);

		// System.out.println("Dia da semana: " + diaDaSemanaAtual +
		// " em relação a " + diaDaSemana);

		if (tipoRecorrencia.equals(RepeticaoReservaEnum.SEMANAL)) {

		    if (diaDaSemanaAtual != diaDaSemana) {
			// Define novo dia e continua o laço
			calendarAtual.add(Calendar.DAY_OF_MONTH, 1);
			continue;
		    }
		}

		for (ItemReserva i : listaItemReserva) {
		    if (listaDeItensARemover.contains(i)) {
			continue;
		    }

		    Reserva r = new Reserva();
		    r.setData(calendarAtual.getTime());
		    r.setHoraFim(horaFim);
		    r.setHoraInicio(horaInicio);
		    r.setIdCampus(campus);
		    r.setIdItemReserva(i);
		    r.setIdPessoa(pessoaLogin);

		    // System.out.println("Conferindo data: " +
		    // calendarAtual.getTime() + " item: " + i.getNome());

		    if (existeConcorrente(r)) {
			listaDeItensARemover.add(i);
			// System.out.println("--> Existe concorrência!");
		    }
		}

		calendarAtual.add(Calendar.DAY_OF_MONTH, 1);
	    }

	    // Cria uma lista de retorno
	    List<ItemReserva> listaDeItensARetornar = new ArrayList<ItemReserva>();

	    for (ItemReserva itemReserva : listaItemReserva) {
		if (!listaDeItensARemover.contains(itemReserva)) {
		    listaDeItensARetornar.add(itemReserva);
		}
	    }

	    return listaDeItensARetornar;
	} else {
	    // Se não for recorrente, retorna a própria lista de itens
	    return listaItemReserva;
	}
    }

    /**
     * Verifica no banco de dados se existe mais que uma reserva para a mesma
     * transação, caracterizando reserva recorrente. Se não houver, retorna null. Se
     * houver, retorna a data final das reservas recorrentes.
     * 
     * @param r
     * @return
     */
    public Date verificaReservaRecorrente(Campus campus, Reserva r) {
	List<Reserva> lista = reservaDAO.listaReservaPorTransacao(campus, StatusReserva.EFETIVADA, r.getIdTransacao().getIdTransacao());

	if (lista != null && lista.size() > 0) {
	    return lista.get(lista.size() - 1).getData();
	}

	return null;
    }

    /**
     * Apaga todas as reservas da transação informada.
     * 
     * @param idTransacao
     */
    public void removerReservasPorTransacao(Campus campus, Integer idTransacao) {
	reservaDAO.removeByTransacao(campus, idTransacao);
    }

    public List<ReservaVO> listToVO(List<Reserva> list) {
	List<ReservaVO> listVO = new ArrayList<ReservaVO>();

	for (Reserva reserva : list) {
	    ReservaVO vo = new ReservaVO();
	    vo.setAutorizar(false);
	    vo.setExcluir(false);
	    vo.setCampus(reserva.getIdCampus());
	    vo.setDataReserva(DateTimeUtils.format(reserva.getData(), "dd/MM/yyyy"));
	    vo.setHoraReserva(DateTimeUtils.format(reserva.getHoraInicio(), "HH:mm") + "-" + DateTimeUtils.format(reserva.getHoraFim(), "HH:mm"));
	    vo.setIdReserva(reserva.getIdReserva());
	    vo.setMotivoReserva(reserva.getMotivo());
	    vo.setNomeItemReserva(reserva.getIdItemReserva().getNome());
	    vo.setUsuarioReserva(reserva.getIdUsuario().getNomeCompleto());
	    vo.setReserva(reserva);
	    vo.setTipoReserva(reserva.getIdTipoReserva().getDescricao());

	    listVO.add(vo);
	}

	return listVO;
    }

    /**
     * Busca todas as reservas de uma transação em formato VO.
     * 
     * @param campus
     * @param idTransacao
     * @return
     */
    public List<ReservaVO> listaReservaPorTransacao(Campus campus, Integer idTransacao) {
	List<Reserva> list = reservaDAO.listaReservaPorTransacao(campus, StatusReserva.EFETIVADA, idTransacao);

	return listToVO(list);
    }

    /**
     * Lista reservas pendentes de autorização para a Pessoa designada.
     * 
     * @param autorizador
     * @return
     */
    public List<ReservaVO> listaReservasPendentes(Campus campus, Pessoa autorizador) {
	List<Reserva> list = reservaDAO.listaReservasPendentes(campus, autorizador);

	return listToVO(list);
    }

    public List<Reserva> pesquisaPorTransacao(Campus campus, Integer idTransacao) {
	return reservaDAO.pesquisaReserva(campus, idTransacao);
    }
}