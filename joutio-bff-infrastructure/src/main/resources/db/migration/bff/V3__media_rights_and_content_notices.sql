-- 2026-11-15 : cadre LCEN/DSA des contenus des joueurs.

-- 1. Déclaration de droits faite par l'auteur de chaque envoi d'image (preuve de son engagement).
ALTER TABLE public.media_asset ADD COLUMN rights_declared_at timestamp without time zone;
ALTER TABLE public.media_asset ADD COLUMN terms_version varchar(32);
ALTER TABLE public.media_asset ADD COLUMN source_note varchar(255);
COMMENT ON COLUMN public.media_asset.rights_declared_at IS 'Déclaration de droits de l''auteur (CGU), null avant le 2026-11-15';

-- 2. Notifications de contenu illicite (LCEN art. 6-I-5, DSA art. 16), reçues par le formulaire public, et leur suite.
CREATE TABLE public.content_notice (
    id uuid PRIMARY KEY,
    status varchar(16) NOT NULL,
    notifier_name varchar(160) NOT NULL,
    notifier_email varchar(254) NOT NULL,
    notifier_quality varchar(16) NOT NULL,
    content_url varchar(1024) NOT NULL,
    category varchar(32) NOT NULL,
    description varchar(4000) NOT NULL,
    rights_evidence varchar(2000),
    good_faith boolean NOT NULL,
    reporter_user_id varchar(64),
    received_at timestamp without time zone NOT NULL,
    decided_at timestamp without time zone,
    decided_by varchar(64),
    decision varchar(32),
    decision_reason varchar(1000)
);
CREATE INDEX idx_content_notice_status ON public.content_notice (status, received_at DESC);
COMMENT ON TABLE public.content_notice IS 'Notifications de contenu illicite (LCEN/DSA) et décisions de la modération';
