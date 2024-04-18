package mchorse.bbs_mod.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.utils.repos.FilmRepository;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import mchorse.bbs_mod.utils.repos.IRepository;
import net.minecraft.client.MinecraftClient;

import java.util.function.Function;
import java.util.function.Supplier;

public class ContentType
{
    private static final IRepository<ParticleScheme> PARTICLE_REPOSITORY = new FolderManagerRepository<>(BBSModClient.getParticles());
    private static final IRepository<Film> FILMS_REPOSITORY = new FolderManagerRepository<>(BBSMod.getFilms());
    private static final IRepository<Film> FILMS_REMOVE_REPOSITORY = new FilmRepository();

    public static final ContentType PARTICLES = new ContentType("particles", () -> PARTICLE_REPOSITORY, (dashboard) -> dashboard.getPanel(UIParticleSchemePanel.class));
    public static final ContentType FILMS = new ContentType("films", () -> MinecraftClient.getInstance().isIntegratedServerRunning() ? FILMS_REPOSITORY : FILMS_REMOVE_REPOSITORY, (dashboard) -> dashboard.getPanel(UIFilmPanel.class));

    private final String id;
    private Supplier<IRepository<? extends ValueGroup>> manager;
    private Function<UIDashboard, UIDataDashboardPanel> dashboardPanel;

    public ContentType(String id, Supplier<IRepository<? extends ValueGroup>> manager, Function<UIDashboard, UIDataDashboardPanel> dashboardPanel)
    {
        this.id = id;
        this.manager = manager;
        this.dashboardPanel = dashboardPanel;
    }

    public String getId()
    {
        return this.id;
    }

    public IRepository<? extends ValueGroup> getRepository()
    {
        return this.manager.get();
    }

    public UIDataDashboardPanel get(UIDashboard dashboard)
    {
        return this.dashboardPanel.apply(dashboard);
    }
}