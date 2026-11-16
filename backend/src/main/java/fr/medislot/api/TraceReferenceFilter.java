package fr.medislot.api;

import io.opentelemetry.api.trace.Span;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Ajoute l'identifiant de trace aux erreurs 5xx, comme référence pour le support. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceReferenceFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var ctx = Span.current().getSpanContext();
        if (!ctx.isValid()) {
            chain.doFilter(req, res);
            return;
        }
        if (req.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR
                && res.getStatus() >= 500) {
            res.setHeader("X-Trace-Id", ctx.getTraceId());
            chain.doFilter(req, res);
            return;
        }
        chain.doFilter(
                req,
                new jakarta.servlet.http.HttpServletResponseWrapper(res) {
                    private void reference(int status) {
                        if (status >= 500 && !isCommitted())
                            setHeader("X-Trace-Id", ctx.getTraceId());
                    }

                    @Override
                    public void setStatus(int sc) {
                        reference(sc);
                        super.setStatus(sc);
                    }

                    @Override
                    public void sendError(int sc) throws IOException {
                        reference(sc);
                        super.sendError(sc);
                    }

                    @Override
                    public void sendError(int sc, String msg) throws IOException {
                        reference(sc);
                        super.sendError(sc, msg);
                    }
                });
    }
}
